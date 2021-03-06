package com.bugsnag.android

import java.nio.ByteBuffer

internal class AnrPlugin : BugsnagPlugin {

    private external fun installAnrDetection(sentinelBuffer: ByteBuffer)

    private val collector = AnrDetailsCollector()

    override fun initialisePlugin(client: Client) {
        System.loadLibrary("bugsnag-plugin-android-anr")
        val delegate: (Thread) -> Unit = { handleAnr(it, client) }
        val monitor = AppNotRespondingMonitor(delegate)
        monitor.start()
        installAnrDetection(monitor.sentinelBuffer)
        Logger.info("Initialised ANR Plugin")
    }

    private fun handleAnr(thread: Thread, client: Client) {
        // generate a full report as soon as possible, then wait for extra process error info
        val errMsg = "Application did not respond to UI input"
        val exc = BugsnagException("ANR", errMsg, thread.stackTrace)
        val error = Error.Builder(client.config, exc, client.sessionTracker, thread, true)
            .severity(Severity.ERROR)
            .severityReasonType(HandledState.REASON_ANR)
            .build()

        // wait and poll for error info to be collected. this occurs just before the ANR dialog
        // is displayed
        collector.collectAnrErrorDetails(client, error)
    }
}
