package com.example.mylocalai.model

import com.google.gson.annotations.SerializedName
import com.google.gson.JsonElement
import com.google.gson.JsonObject

data class ChatRequest(
    val session_id: String,
    val model: String = "qwen3.5:4b",
    val prompt: String,
    // "direct" keeps work in the conversation; "full" allows durable task delegation.
    val tool_mode: String = "direct",
    val images: List<String> = emptyList(),
    val allowed_tools: List<String>? = null,
    val plan_approved: Boolean = false,
    val language: String? = null,
)

data class SoftwareTaskOffer(
    val task_id: String = "",
    val status: String = "queued",
    val phase: String = "",
    val reused: Boolean = false,
    val status_url: String? = null,
    val events_url: String? = null,
    val diff_url: String? = null,
)

data class ChatResponse(
    val request_id: String? = null,
    val session_id: String? = null,
    val assistant_text: String? = null,
    val message: MessageDto? = null,
    val actions: List<JsonElement> = emptyList(),
    val tool_results: List<ToolResultDto> = emptyList(),
    val ollama: JsonObject? = null,
    val ollama_queue_wait_ms: JsonElement? = null,
    val execution_status: String? = null,
    val software_tasks: List<SoftwareTaskOffer> = emptyList(),
    val error: String? = null,
)

data class MessageDto(
    val role: String,
    val content: String,
)

data class AsyncChatJobCreated(
    val request_id: String = "",
    val status: String = "queued",
    val reused: Boolean = false,
    val status_url: String? = null,
    val events_url: String? = null,
)

data class AsyncChatJobStatus(
    val request_id: String = "",
    val status: String = "",
    val response: ChatResponse? = null,
    val error: String? = null,
    val task_id: String? = null,
    val task_url: String? = null,
    val elapsed_seconds: Double? = null,
    val stream_url: String? = null,
)

data class AsyncChatJobEvent(
    val event_id: Long = 0,
    val event_type: String = "",
    val message: String = "",
    val data: JsonElement? = null,
    val created_at: String? = null,
)

data class AsyncChatJobEventsPage(
    val request_id: String = "",
    val events: List<AsyncChatJobEvent> = emptyList(),
    val next_after_id: Long = 0,
)

data class ServerHealthMetrics(
    val availableMemoryBytes: Long? = null,
    val diskFreeBytes: Long? = null,
    val diskFreePercent: Float? = null,
    val chatJobsPending: Int? = null,
    val staleTaskHeartbeats: Int? = null,
    val chatJobsRunning: Int? = null,
)

data class SystemStatsResponse(
    val uptime: String,
    val memory: String,
    val disk: String,
    @SerializedName(value = "cpu_usage", alternate = ["cpu"])
    val cpu: String = "N/A",
)

data class ToolResultDto(
    val tool: String = "tool",
    val arguments: JsonObject? = null,
    val output: String? = null,
    val data: JsonElement? = null,
    val exit_code: Int? = null,
    val stdout: String? = null,
    val stderr: String? = null,
    val timed_out: Boolean? = null,
    val error: String? = null,
    val download_url: String? = null,
    val diff: String? = null,
)

data class CommandRequest(
    val command: String,
    val cwd: String = "",
    val timeout_seconds: Int = 120,
)

data class CommandResultDto(
    val exit_code: Int? = null,
    val stdout: String? = null,
    val stderr: String? = null,
    val timed_out: Boolean = false,
)

data class CommandResponse(
    val message: MessageDto? = null,
    val result: CommandResultDto? = null,
    // The bridge may report fractional milliseconds (for example 2.8).
    val duration_ms: Double? = null,
    val error: String? = null,
)

data class JobDto(
    val job_id: String = "",
    val status: String = "unknown",
    val command: String = "",
    val cwd: String = "",
    val timeout_seconds: Int? = null,
    val stdout: String = "",
    val stderr: String = "",
    val exit_code: Int? = null,
    val duration_ms: Double? = null,
    val error: String? = null,
)

data class JobEnvelope(val job: JobDto? = null)
data class JobListResponse(val jobs: List<JobDto> = emptyList())

data class SoftwareTaskRequest(
    val objective: String,
    val workspace: String,
    val acceptance_criteria: List<String>,
    val verify_commands: List<String>,
    val model: String,
    val use_worktree: Boolean = true,
    val max_steps: Int = 24,
    val max_seconds: Int = 21600,
)

data class SoftwareTaskResumeRequest(
    val message: String,
    val additional_steps: Int? = null,
    val additional_seconds: Int? = null,
)

data class SoftwareTaskSummary(
    val taskId: String,
    val objective: String = "",
    val status: String = "unknown",
    val phase: String = "",
    val stepsDone: Int? = null,
    val stepsTotal: Int? = null,
    val activeJobId: String? = null,
    val workspacePath: String? = null,
    val worktreePath: String? = null,
    val statusUrl: String? = null,
    val eventsUrl: String? = null,
    val diffUrl: String? = null,
    val reused: Boolean = false,
    val plan: String? = null,
    val error: String? = null,
    val finalReport: String? = null,
    val rawJson: String = "",
)

data class SoftwareTaskEvent(
    val id: Long,
    val eventType: String = "event",
    val message: String = "",
    val rawJson: String = "",
)

data class SoftwareTaskEventsPage(val events: List<SoftwareTaskEvent>, val nextAfterId: Long)

data class SoftwareTaskDiff(
    val baseCommit: String = "",
    val worktreePath: String = "",
    val diff: String = "",
    val untrackedFiles: List<String> = emptyList(),
    val truncated: Boolean = false,
)

data class TrackedSoftwareTask(
    val taskId: String,
    val baseUrl: String,
    val eventCursor: Long = 0,
    val statusUrl: String? = null,
    val eventsUrl: String? = null,
    val diffUrl: String? = null,
)
data class PendingSoftwareTaskCreate(val idempotencyKey: String, val baseUrl: String, val request: SoftwareTaskRequest)
data class PendingAgentChatRequest(
    val idempotencyKey: String,
    val baseUrl: String,
    val request: ChatRequest,
    val userMessageId: String = "",
    val requestFingerprint: String = "",
)

data class PersistedChatJob(
    val requestId: String = "",
    val sessionId: String = "",
    val assistantMessageId: String = "",
    val baseUrl: String = "",
    val idempotencyKey: String = "",
    val startedAtMs: Long = 0L,
    val eventCursor: Long = 0L,
    val prompt: String = "",
)

data class SessionResetResponse(
    val session_id: String = "",
    val status: String = "",
)

data class SearchResultDto(val title: String = "", val url: String = "", val snippet: String = "")
data class SearchResponse(val query: String = "", val results: List<SearchResultDto> = emptyList(), val error: String? = null)

data class ServiceRequest(val name: String, val action: String)
data class PackageRequest(val action: String, @SerializedName("package") val packageName: String = "")
data class ScheduleRequest(val action: String, val schedule: String = "", val command: String = "", val unit: String = "")
data class AppLaunchRequest(val application: String, val args: List<String> = emptyList())
data class DesktopRequest(
    val action: String,
    val text: String = "",
    val key: String = "",
    val x: Int? = null,
    val y: Int? = null,
    val button: Int = 1,
)

data class AgentNoteDto(
    @SerializedName(value = "id", alternate = ["note_id"])
    val id: Long = 0,
    val source: String = "",
    val category: String = "",
    val note: String = "",
    @SerializedName(value = "created_at", alternate = ["timestamp"])
    val created_at: String = "",
)

data class AgentNotesResponse(val notes: List<AgentNoteDto> = emptyList(), val next_after_id: Long = 0)
data class AgentNoteRequest(val source: String, val category: String, val note: String)

data class BridgeAuditEntry(
    val id: String,
    val timestamp: Long,
    val action: String,
    val detail: String,
    val succeeded: Boolean,
)
