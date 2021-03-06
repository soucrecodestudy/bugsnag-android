package com.bugsnag.android

import android.app.ActivityManager
import android.app.ActivityManager.ProcessErrorStateInfo
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import androidx.annotation.VisibleForTesting
import java.util.concurrent.atomic.AtomicInteger

internal class AnrDetailsCollector {

    companion object {
        private const val INFO_POLL_THRESHOLD_MS: Long = 100
        private const val MAX_ATTEMPTS: Int = 300
    }

    private val handlerThread = HandlerThread("bugsnag-anr-collector")

    init {
        handlerThread.start()
    }

    fun collectAnrDetails(ctx: Context): ProcessErrorStateInfo? {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return captureProcessErrorState(am, Process.myPid())
    }

    /**
     * Collects information about an ANR, by querying an activity manager for information about
     * any proceses which are currently in an error condition.
     *
     * See https://developer.android.com/reference/android/app/ActivityManager.html#getProcessesInErrorState()
     */
    @VisibleForTesting
    internal fun captureProcessErrorState(am: ActivityManager, pid: Int): ProcessErrorStateInfo? {
        return try {
            val processes = am.processesInErrorState ?: emptyList()
            processes.firstOrNull { it.pid == pid }
        } catch (exc: RuntimeException) {
            null
        }
    }

    internal fun addErrorStateInfo(error: Error, anrState: ProcessErrorStateInfo) {
        val msg = anrState.shortMsg
        error.exceptionMessage = when {
            msg.startsWith("ANR") -> msg.replaceFirst("ANR", "")
            else -> msg
        }
    }

    internal fun collectAnrErrorDetails(client: Client, error: Error) {
        val handler = Handler(handlerThread.looper)
        val attempts = AtomicInteger()

        handler.post(object : Runnable {
            override fun run() {
                val anrDetails = collectAnrDetails(client.appContext)

                if (anrDetails == null) {
                    if (attempts.getAndIncrement() < MAX_ATTEMPTS) {
                        handler.postDelayed(this, INFO_POLL_THRESHOLD_MS)
                    }
                } else {
                    addErrorStateInfo(error, anrDetails)
                    client.notify(error, DeliveryStyle.ASYNC_WITH_CACHE, null)
                }
            }
        })
    }
}
