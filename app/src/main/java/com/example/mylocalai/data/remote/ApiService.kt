package com.example.mylocalai.data.remote

import com.example.mylocalai.model.ChatRequest
import com.example.mylocalai.model.ChatResponse
import com.example.mylocalai.model.AsyncChatJobCreated
import com.example.mylocalai.model.AsyncChatJobStatus
import com.example.mylocalai.model.AsyncChatJobEventsPage
import com.example.mylocalai.model.SystemStatsResponse
import com.example.mylocalai.model.CommandRequest
import com.example.mylocalai.model.CommandResponse
import com.example.mylocalai.model.JobEnvelope
import com.example.mylocalai.model.JobListResponse
import com.example.mylocalai.model.SearchResponse
import com.example.mylocalai.model.ServiceRequest
import com.example.mylocalai.model.PackageRequest
import com.example.mylocalai.model.ScheduleRequest
import com.example.mylocalai.model.AppLaunchRequest
import com.example.mylocalai.model.DesktopRequest
import com.example.mylocalai.model.AgentNotesResponse
import com.example.mylocalai.model.AgentNoteRequest
import com.example.mylocalai.model.SoftwareTaskRequest
import com.example.mylocalai.model.SoftwareTaskResumeRequest
import com.example.mylocalai.model.SessionResetResponse
import com.example.mylocalai.model.SessionStateDto
import com.example.mylocalai.model.PlanRequest
import com.example.mylocalai.model.PlanResponse
import com.example.mylocalai.model.ReviewRequest
import com.example.mylocalai.model.ReviewResponse
import com.example.mylocalai.model.FileWriteRequest
import com.example.mylocalai.model.FileTransferRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query
import retrofit2.http.Path
import retrofit2.http.DELETE
import retrofit2.http.Header
import retrofit2.http.Url
import retrofit2.http.Streaming
import okhttp3.ResponseBody
import retrofit2.Call

interface ApiService {
    @GET("api/session-state")
    suspend fun getSessionState(@Query("session_id") sessionId: String): SessionStateDto

    @POST("api/plan")
    suspend fun createPlan(@Body request: PlanRequest): PlanResponse

    @POST("api/review")
    suspend fun reviewFiles(@Body request: ReviewRequest): ReviewResponse

    @PUT("api/file")
    suspend fun writeRemoteFile(@Body request: FileWriteRequest): ResponseBody

    @POST("api/chat")
    suspend fun chat(@Body request: ChatRequest): ChatResponse

    @POST("api/chat")
    suspend fun chat(
        @Header("Idempotency-Key") idempotencyKey: String?,
        @Body request: ChatRequest,
    ): ChatResponse

    @POST("api/chat/jobs")
    suspend fun createAgentChatJob(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: ChatRequest,
    ): AsyncChatJobCreated

    @GET("api/chat/jobs/{request_id}")
    suspend fun getAgentChatJob(@Path("request_id") requestId: String): AsyncChatJobStatus

    @GET("api/chat/jobs/{request_id}/events")
    suspend fun getAgentChatJobEvents(
        @Path("request_id") requestId: String,
        @Query("after_id") afterId: Long,
        @Query("limit") limit: Int = 100,
    ): AsyncChatJobEventsPage

    @GET("api/chat/jobs/{request_id}/events/stream")
    fun streamAgentChatJobEvents(@Path("request_id") requestId: String): Call<ResponseBody>

    @DELETE("api/chat/jobs/{request_id}")
    suspend fun cancelAgentChatJob(@Path("request_id") requestId: String): ResponseBody

    @POST("api/sessions/{session_id}/reset")
    suspend fun resetSession(@Path("session_id") sessionId: String): SessionResetResponse

    @GET("api/stats")
    suspend fun getStats(): SystemStatsResponse

    @GET("api/health")
    suspend fun getHealth(): ResponseBody

    @POST("api/command")
    suspend fun runCommand(@Body request: CommandRequest): CommandResponse

    @POST("api/jobs")
    suspend fun startJob(@Body request: CommandRequest): JobEnvelope

    @GET("api/jobs")
    suspend fun getJobs(@Query("limit") limit: Int = 50): JobListResponse

    @GET("api/jobs/{job_id}")
    suspend fun getJob(@Path("job_id") jobId: String): ResponseBody

    @DELETE("api/jobs/{job_id}")
    suspend fun cancelJob(@Path("job_id") jobId: String): ResponseBody

    @GET("api/files")
    suspend fun listFiles(
        @Query("path") path: String,
        @Query("limit") limit: Int = 500,
        @Query("as_root") asRoot: Boolean = false,
    ): ResponseBody

    @POST("api/files/transfer")
    suspend fun transferFile(@Body request: FileTransferRequest): ResponseBody

    @GET("api/search")
    suspend fun search(@Query("q") query: String, @Query("max_results") maxResults: Int = 5): SearchResponse

    @GET("api/processes")
    suspend fun getProcesses(): ResponseBody

    @POST("api/services")
    suspend fun serviceAction(@Body request: ServiceRequest): ResponseBody

    @POST("api/packages")
    suspend fun packageAction(@Body request: PackageRequest): ResponseBody

    @POST("api/schedules")
    suspend fun scheduleAction(@Body request: ScheduleRequest): ResponseBody

    @GET("api/apps")
    suspend fun getApps(@Query("query") query: String = "", @Query("limit") limit: Int = 100): ResponseBody

    @POST("api/apps/launch")
    suspend fun launchApp(@Body request: AppLaunchRequest): ResponseBody

    @POST("api/desktop")
    suspend fun desktopAction(@Body request: DesktopRequest): ResponseBody

    @GET("api/desktop/diagnostics")
    suspend fun desktopDiagnostics(): ResponseBody

    @GET("api/logs")
    suspend fun getLogs(@Query("lines") lines: Int = 200): ResponseBody

    @GET("api/tags")
    suspend fun getTags(@Query("refresh") refresh: Boolean = false): ResponseBody

    @GET("api/agent-bridge/help")
    suspend fun getBridgeHelp(@Query("include_runtime") includeRuntime: Boolean = true): ResponseBody

    @GET("api/agent-bridge/notes")
    suspend fun getAgentNotes(@Query("limit") limit: Int = 50, @Query("after_id") afterId: Long = 0): AgentNotesResponse

    @POST("api/agent-bridge/notes")
    suspend fun postAgentNote(@Body request: AgentNoteRequest): ResponseBody

    @POST("api/tasks")
    suspend fun createSoftwareTask(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: SoftwareTaskRequest,
    ): ResponseBody

    @GET("api/tasks/{task_id}")
    suspend fun getSoftwareTask(@Path("task_id") taskId: String): ResponseBody

    @GET
    suspend fun getSoftwareTaskAt(@Url statusUrl: String): ResponseBody

    @GET("api/tasks/{task_id}/events")
    suspend fun getSoftwareTaskEvents(
        @Path("task_id") taskId: String,
        @Query("after_id") afterId: Long,
    ): ResponseBody

    @GET
    suspend fun getSoftwareTaskEventsAt(@Url eventsUrl: String, @Query("after_id") afterId: Long): ResponseBody

    @POST("api/tasks/{task_id}/resume")
    suspend fun resumeSoftwareTask(@Path("task_id") taskId: String, @Body request: SoftwareTaskResumeRequest): ResponseBody

    @DELETE("api/tasks/{task_id}")
    suspend fun cancelSoftwareTask(@Path("task_id") taskId: String): ResponseBody

    @GET("api/tasks/{task_id}/diff")
    suspend fun getSoftwareTaskDiff(@Path("task_id") taskId: String): ResponseBody

    @GET
    suspend fun getSoftwareTaskDiffAt(@Url diffUrl: String): ResponseBody

    @DELETE("api/tasks/{task_id}/worktree")
    suspend fun deleteSoftwareTaskWorktree(@Path("task_id") taskId: String, @Query("confirm") confirm: Boolean = true): ResponseBody

    @Streaming
    @POST("api/chat/stream")
    fun streamChat(@Body request: ChatRequest): Call<ResponseBody>
}
