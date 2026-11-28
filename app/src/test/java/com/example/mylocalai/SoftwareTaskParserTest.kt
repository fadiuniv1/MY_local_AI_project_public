package com.example.mylocalai

import com.example.mylocalai.data.remote.SoftwareTaskParser
import com.example.mylocalai.model.ChatRequest
import com.example.mylocalai.model.ChatResponse
import com.example.mylocalai.model.PendingAgentChatRequest
import com.example.mylocalai.model.SoftwareTaskOffer
import com.example.mylocalai.model.ToolResultDto
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoftwareTaskParserTest {

    @Test
    fun parseTask_extractsAllFieldsCorrectly() {
        val json = """
            {
                "task": {
                    "task_id": "task-123",
                    "objective": "Build unit tests",
                    "status": "running",
                    "phase": "executing",
                    "step_count": 3,
                    "max_steps": 10,
                    "status_url": "/api/tasks/task-123",
                    "events_url": "/api/tasks/task-123/events",
                    "diff_url": "/api/tasks/task-123/diff",
                    "reused": true
                }
            }
        """.trimIndent()

        val summary = SoftwareTaskParser.parseTask(json)
        assertEquals("task-123", summary.taskId)
        assertEquals("Build unit tests", summary.objective)
        assertEquals("running", summary.status)
        assertEquals("executing", summary.phase)
        assertEquals(3, summary.stepsDone)
        assertEquals(10, summary.stepsTotal)
        assertEquals("/api/tasks/task-123", summary.statusUrl)
        assertEquals("/api/tasks/task-123/events", summary.eventsUrl)
        assertEquals("/api/tasks/task-123/diff", summary.diffUrl)
        assertTrue(summary.reused)
    }

    @Test
    fun parseEvents_handlesMultipleEventsAndCursor() {
        val json = """
            {
                "events": [
                    { "id": 1, "type": "start", "message": "Task started" },
                    { "id": 2, "type": "step", "message": "Step 1 complete" }
                ],
                "next_after_id": 2
            }
        """.trimIndent()

        val page = SoftwareTaskParser.parseEvents(json, cursor = 0L)
        assertEquals(2, page.events.size)
        assertEquals(1L, page.events[0].id)
        assertEquals("start", page.events[0].eventType)
        assertEquals("Task started", page.events[0].message)
        assertEquals(2L, page.nextAfterId)
    }

    @Test
    fun parseEvents_neverRegressCursor() {
        val json = """
            {
                "events": [
                    { "id": 1, "type": "start", "message": "Task started" },
                    { "id": 2, "type": "step", "message": "Step 1 complete" }
                ],
                "next_after_id": 0
            }
        """.trimIndent()

        val page = SoftwareTaskParser.parseEvents(json, cursor = 0L)
        assertEquals(2L, page.nextAfterId)
    }

    @Test
    fun parseDiff_handlesUntrackedFilesAndDiff() {
        val json = """
            {
                "base_commit": "abc1234",
                "worktree_path": "/tmp/worktree",
                "diff": "--- a/file\n+++ b/file",
                "untracked_files": ["new_file.txt"],
                "truncated": false
            }
        """.trimIndent()

        val diff = SoftwareTaskParser.parseDiff(json)
        assertEquals("abc1234", diff.baseCommit)
        assertEquals("/tmp/worktree", diff.worktreePath)
        assertEquals("--- a/file\n+++ b/file", diff.diff)
        assertEquals(listOf("new_file.txt"), diff.untrackedFiles)
        assertFalse(diff.truncated)
    }

    @Test
    fun chatResponse_serializesAndDeserializesSoftwareTasksAndToolResults() {
        val gson = Gson()
        val json = """
            {
                "request_id": "req-999",
                "session_id": "session-1",
                "assistant_text": "Task scheduled",
                "execution_status": "task_queued",
                "software_tasks": [
                    {
                        "task_id": "task-456",
                        "status": "queued",
                        "phase": "planning",
                        "status_url": "/api/tasks/task-456"
                    }
                ],
                "tool_results": [
                    {
                        "tool": "bash",
                        "exit_code": 0,
                        "stdout": "success"
                    }
                ]
            }
        """.trimIndent()

        val response = gson.fromJson(json, ChatResponse::class.java)
        assertEquals("req-999", response.request_id)
        assertEquals("task_queued", response.execution_status)
        assertEquals(1, response.software_tasks.size)
        assertEquals("task-456", response.software_tasks[0].task_id)
        assertEquals("queued", response.software_tasks[0].status)
        assertEquals("/api/tasks/task-456", response.software_tasks[0].status_url)
        assertEquals(1, response.tool_results.size)
        assertEquals("bash", response.tool_results[0].tool)
        assertEquals(0, response.tool_results[0].exit_code)
    }

    @Test
    fun pendingAgentChatRequest_serializesAndDeserializesCorrectly() {
        val gson = Gson()
        val request = PendingAgentChatRequest(
            idempotencyKey = "key-12345",
            baseUrl = "http://127.0.0.1:8081",
            request = ChatRequest(
                session_id = "session-test",
                prompt = "Hello AI"
            ),
            userMessageId = "msg-1",
            requestFingerprint = "fingerprint-abc"
        )

        val json = gson.toJson(request)
        val deserialized = gson.fromJson(json, PendingAgentChatRequest::class.java)

        assertEquals("key-12345", deserialized.idempotencyKey)
        assertEquals("http://127.0.0.1:8081", deserialized.baseUrl)
        assertEquals("session-test", deserialized.request.session_id)
        assertEquals("Hello AI", deserialized.request.prompt)
        assertEquals("msg-1", deserialized.userMessageId)
        assertEquals("fingerprint-abc", deserialized.requestFingerprint)
    }

    @Test
    fun parseModelsResponse_supportsObjectsAndStrings() {
        val json = """
            {
                "models": [
                    { "name": "qwen3.5:4b" },
                    { "model": "llama3.2:3b" },
                    "deepseek-coder:6.7b"
                ]
            }
        """.trimIndent()

        val root = org.json.JSONObject(json)
        val array = root.optJSONArray("models") ?: org.json.JSONArray()
        val extracted = mutableListOf<String>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i)
            val name = if (obj != null) {
                obj.optString("name", "").ifBlank { obj.optString("model", "") }
            } else {
                array.optString(i, "")
            }
            if (name.isNotBlank() && name !in extracted) {
                extracted.add(name)
            }
        }

        assertEquals(listOf("qwen3.5:4b", "llama3.2:3b", "deepseek-coder:6.7b"), extracted)
    }
}
