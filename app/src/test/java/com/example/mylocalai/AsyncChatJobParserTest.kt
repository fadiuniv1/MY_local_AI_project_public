package com.example.mylocalai

import com.example.mylocalai.data.remote.JobResponseParser
import com.example.mylocalai.model.AsyncChatJobCreated
import com.example.mylocalai.model.AsyncChatJobEvent
import com.example.mylocalai.model.AsyncChatJobEventsPage
import com.example.mylocalai.model.AsyncChatJobStatus
import com.example.mylocalai.model.PersistedChatJob
import com.example.mylocalai.model.ChatMessage
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AsyncChatJobParserTest {

    @Test
    fun jobResponseParser_rejectsNullFieldsWithoutThrowing() {
        assertNull(JobResponseParser.parse("""{"job":{"job_id":null,"status":"running"}}"""))
        assertNull(JobResponseParser.parse("""{"job":{"job_id":"req-1","status":null}}"""))
        assertNotNull(JobResponseParser.parse("""{"job":{"job_id":"req-1","status":"running"}}"""))
    }

    @Test
    fun asyncChatJobCreated_deserializes202Body() {
        val gson = Gson()
        val json = """
            {"request_id": "req-1", "status": "queued", "reused": false,
             "status_url": "/api/chat/jobs/req-1", "events_url": "/api/chat/jobs/req-1/events"}
        """.trimIndent()

        val created = gson.fromJson(json, AsyncChatJobCreated::class.java)

        assertEquals("req-1", created.request_id)
        assertEquals("queued", created.status)
        assertFalse(created.reused)
        assertEquals("/api/chat/jobs/req-1", created.status_url)
        assertEquals("/api/chat/jobs/req-1/events", created.events_url)
    }

    @Test
    fun asyncChatJobCreated_deserializesDuplicateSendWithReusedFlag() {
        val gson = Gson()
        val json = """
            {"request_id": "req-1", "status": "queued", "reused": true}
        """.trimIndent()

        val created = gson.fromJson(json, AsyncChatJobCreated::class.java)

        assertEquals("req-1", created.request_id)
        assertTrue(created.reused)
    }

    @Test
    fun asyncChatJobStatus_deserializesTerminalStatusWithResponse() {
        val gson = Gson()
        val json = """
            {
                "request_id": "req-1",
                "status": "completed",
                "response": {
                    "request_id": "req-1",
                    "session_id": "session-1",
                    "message": {"role": "assistant", "content": "Done"},
                    "execution_status": "completed"
                }
            }
        """.trimIndent()

        val status = gson.fromJson(json, AsyncChatJobStatus::class.java)

        assertEquals("req-1", status.request_id)
        assertEquals("completed", status.status)
        assertEquals("Done", status.response?.message?.content)
        assertEquals("session-1", status.response?.session_id)
    }

    @Test
    fun asyncChatJobStatus_deserializesElapsedSecondsAndStreamUrl() {
        val gson = Gson()
        val json = """
            {"request_id": "r1", "status": "running", "elapsed_seconds": 97.0,
             "stream_url": "/api/chat/jobs/r1/events/stream"}
        """.trimIndent()

        val status = gson.fromJson(json, AsyncChatJobStatus::class.java)

        assertTrue(status.elapsed_seconds == 97.0)
        assertTrue(status.stream_url == "/api/chat/jobs/r1/events/stream")
    }

    @Test
    fun asyncChatJobStatus_deserializesDelegatedWithTaskId() {
        val gson = Gson()
        val json = """
            {"request_id": "req-2", "status": "delegated", "task_id": "task-9", "task_url": "/api/tasks/task-9"}
        """.trimIndent()

        val status = gson.fromJson(json, AsyncChatJobStatus::class.java)

        assertEquals("delegated", status.status)
        assertEquals("task-9", status.task_id)
        assertEquals("/api/tasks/task-9", status.task_url)
    }

    @Test
    fun asyncChatJobStatus_deserializesFailedWithError() {
        val gson = Gson()
        val json = """
            {"request_id": "req-3", "status": "failed", "error": "model timed out"}
        """.trimIndent()

        val status = gson.fromJson(json, AsyncChatJobStatus::class.java)

        assertEquals("failed", status.status)
        assertEquals("model timed out", status.error)
    }

    @Test
    fun asyncChatJobStatus_deserializesActiveStatusWithoutResponse() {
        val gson = Gson()
        val json = """
            {"request_id": "req-4", "status": "running"}
        """.trimIndent()

        val status = gson.fromJson(json, AsyncChatJobStatus::class.java)

        assertEquals("running", status.status)
        assertNotNull(status)
        assertTrue(status.response == null || status.response!!.message == null)
    }

    @Test
    fun asyncChatJobEventsPage_deserializesEventsAndCursor() {
        val gson = Gson()
        val json = """
            {
                "request_id": "req-1",
                "events": [
                    {"event_id": 1, "event_type": "accepted", "message": "Job accepted", "created_at": "2026-11-20T00:00:00Z"},
                    {"event_id": 2, "event_type": "tool_started", "message": "Running ls"},
                    {"event_id": 3, "event_type": "verification_result", "data": {"passed": true}}
                ],
                "next_after_id": 3
            }
        """.trimIndent()

        val page = gson.fromJson(json, AsyncChatJobEventsPage::class.java)

        assertEquals("req-1", page.request_id)
        assertEquals(3, page.events.size)
        assertEquals(1L, page.events[0].event_id)
        assertEquals("accepted", page.events[0].event_type)
        assertEquals("Job accepted", page.events[0].message)
        assertEquals("2026-11-20T00:00:00Z", page.events[0].created_at)
        assertEquals(2L, page.events[1].event_id)
        assertEquals("Running ls", page.events[1].message)
        assertNotNull(page.events[2].data)
        assertEquals(3L, page.next_after_id)
    }

    @Test
    fun persistedChatJob_deserializesAllFields() {
        val gson = Gson()
        val json = """
            {"requestId":"req-1","sessionId":"session-1","assistantMessageId":"msg-1",
             "baseUrl":"http://agent-host:8081","idempotencyKey":"key-1",
             "startedAtMs":1700000000000,"eventCursor":35,"prompt":"Say hi"}
        """.trimIndent()

        val job = gson.fromJson(json, PersistedChatJob::class.java)

        assertEquals("req-1", job.requestId)
        assertEquals("session-1", job.sessionId)
        assertEquals("msg-1", job.assistantMessageId)
        assertEquals("http://agent-host:8081", job.baseUrl)
        assertEquals("key-1", job.idempotencyKey)
        assertTrue(job.startedAtMs == 1700000000000L)
        assertTrue(job.eventCursor == 35L)
        assertTrue(job.prompt == "Say hi")
    }

    @Test
    fun chatMessage_failureFlagIsExplicitNotDerivedFromProse() {
        val ordinaryAnswer = ChatMessage(role = "assistant", content = "An error can occur in ordinary prose")
        assertFalse(ordinaryAnswer.isJobFailure)
        val failed = ordinaryAnswer.copy(isJobFailure = true)
        assertTrue(failed.isJobFailure)
    }

    @Test
    fun asyncChatJobEvent_deserializesMinimalEvent() {
        val gson = Gson()
        val json = """{"event_id": 7, "event_type": "model_step"}"""

        val event = gson.fromJson(json, AsyncChatJobEvent::class.java)

        assertEquals(7L, event.event_id)
        assertEquals("model_step", event.event_type)
        assertEquals("", event.message)
    }

    @Test
    fun asyncChatJobEvent_deserializesToolStartedAndResultEvents() {
        val gson = Gson()
        val startedJson = """{"event_id": 10, "event_type": "tool_started", "message": "Preparing write_file"}"""
        val resultJson = """{"event_id": 11, "event_type": "tool_result", "message": "Finished write_file"}"""

        val started = gson.fromJson(startedJson, AsyncChatJobEvent::class.java)
        val result = gson.fromJson(resultJson, AsyncChatJobEvent::class.java)

        assertEquals("tool_started", started.event_type)
        assertEquals("Preparing write_file", started.message)
        assertEquals("tool_result", result.event_type)
        assertEquals("Finished write_file", result.message)
    }

    @Test
    fun asyncChatJobEvent_deserializesHeartbeatModelStep() {
        val gson = Gson()
        val json = """{"event_id": 12, "event_type": "model_step", "message": "Working (15s)"}"""

        val event = gson.fromJson(json, AsyncChatJobEvent::class.java)

        assertEquals("model_step", event.event_type)
        assertEquals("Working (15s)", event.message)
    }

    @Test
    fun asyncChatJobEvent_deserializesStreamingTokenEvent() {
        val gson = Gson()
        val json = """{"event_id": 13, "event_type": "token", "message": "Hello world"}"""

        val event = gson.fromJson(json, AsyncChatJobEvent::class.java)

        assertEquals("token", event.event_type)
        assertEquals("Hello world", event.message)
    }
}
