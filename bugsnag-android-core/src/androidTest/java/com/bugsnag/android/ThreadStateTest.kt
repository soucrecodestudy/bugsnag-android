package com.bugsnag.android

import androidx.test.filters.SmallTest
import com.bugsnag.android.BugsnagTestUtils.streamableToJsonArray
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

@SmallTest
class ThreadStateTest {

    private val configuration = Configuration("api-key")
    private val threadState = ThreadState(configuration, Thread.currentThread(), Thread.getAllStackTraces(), null)
    private val json = streamableToJsonArray(threadState)

    /**
     * Verifies that the required values for 'thread' are serialised as an array
     */
    @Test
    fun testSerialisation() {
        for (k in 0 until json.length()) {
            val thread = json[k] as JSONObject
            assertNotNull(thread.getString("id"))
            assertNotNull(thread.getString("name"))
            assertNotNull(thread.getString("stacktrace"))
            assertEquals("android", thread.getString("type"))
        }
    }

    /**
     * Verifies that the current thread is serialised as an object, and that only this value
     * contains the errorReportingThread boolean flag
     */
    @Test
    fun testCurrentThread() {
        verifyCurrentThreadStructure(json, Thread.currentThread().id)
    }

    /**
     * Verifies that a thread different from the current thread is serialised as an object,
     * and that only this value contains the errorReportingThread boolean flag
     */
    @Test
    fun testDifferentThread() {
        val otherThread = Thread.getAllStackTraces()
            .filter { it.key != Thread.currentThread() }
            .map { it.key }
            .first()

        val state = ThreadState(configuration, otherThread, Thread.getAllStackTraces(), null)
        val json = streamableToJsonArray(state)
        verifyCurrentThreadStructure(json, otherThread.id)
    }

    /**
     * Verifies that if the current thread is missing from the available traces as reported by
     * [Thread.getAllStackTraces], its stacktrace will still be serialised
     */
    @Test
    fun testMissingCurrentThread() {
        val currentThread = Thread.currentThread()
        val missingTraces = Thread.getAllStackTraces()
        missingTraces.remove(currentThread)

        val state = ThreadState(configuration, currentThread, missingTraces, null)
        val json = streamableToJsonArray(state)

        verifyCurrentThreadStructure(json, currentThread.id) {
            assertTrue(it.getJSONArray("stacktrace").length() > 0)
        }
    }

    /**
     * Verifies that a handled error uses [Thread] for the reporting thread stacktrace
     */
    @Test
    fun testHandledStacktrace() {
        val currentThread = Thread.currentThread()
        val allStackTraces = Thread.getAllStackTraces()
        val state = ThreadState(configuration, currentThread, allStackTraces, null)
        val json = streamableToJsonArray(state)

        // find the stack trace for the current thread that was passed as a parameter
        val expectedTrace = allStackTraces.filter {
            it.key.id == currentThread.id
        }.map { it.value }.first()

        verifyCurrentThreadStructure(json, currentThread.id) {

            // the thread id + name should always be used
            assertEquals(currentThread.name, it.getString("name"))
            assertEquals(currentThread.id, it.getLong("id"))

            // stacktrace should come from the thread (check same length and line numbers)
            val serialisedTrace = it.getJSONArray("stacktrace")
            assertEquals(expectedTrace.size, serialisedTrace.length())

            expectedTrace.forEachIndexed { index, element ->
                val jsonObject = serialisedTrace.getJSONObject(index)
                assertEquals(element.lineNumber, jsonObject.getInt("lineNumber"))
            }
        }
    }

    /**
     * * Verifies that an unhandled error uses [Exception] for the reporting thread stacktrace
     */
    @Test
    fun testUnhandledStacktrace() {
        val currentThread = Thread.currentThread()
        val allStackTraces = Thread.getAllStackTraces()
        val exc: Throwable = RuntimeException("Whoops")
        val expectedTrace = exc.stackTrace

        val state = ThreadState(configuration, currentThread, allStackTraces, exc)
        val json = streamableToJsonArray(state)

        verifyCurrentThreadStructure(json, currentThread.id) {

            // the thread id + name should always be used
            assertEquals(currentThread.name, it.getString("name"))
            assertEquals(currentThread.id, it.getLong("id"))

            // stacktrace should come from the exception (check different length)
            val serialisedTrace = it.getJSONArray("stacktrace")
            assertEquals(expectedTrace.size, serialisedTrace.length())

            expectedTrace.forEachIndexed { index, element ->
                val jsonObject = serialisedTrace.getJSONObject(index)
                assertEquals(element.lineNumber, jsonObject.getInt("lineNumber"))
            }
        }
    }

    private fun verifyCurrentThreadStructure(json: JSONArray,
                                             currentThreadId: Long,
                                             action: ((thread: JSONObject) -> Unit)? = null) {
        var currentThreadCount = 0

        for (k in 0 until json.length()) {
            val thread = json[k] as JSONObject
            val threadId = thread.getLong("id")

            if (threadId == currentThreadId) {
                assertTrue(thread.getBoolean("errorReportingThread"))
                currentThreadCount++
                action?.invoke(thread)
            } else {
                assertFalse(thread.has("errorReportingThread"))
            }
        }
        assertEquals("Expected one error reporting thread", 1, currentThreadCount)
    }

}
