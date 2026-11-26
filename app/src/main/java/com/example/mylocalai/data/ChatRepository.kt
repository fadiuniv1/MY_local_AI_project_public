package com.example.mylocalai.data

import android.content.Context
import com.example.mylocalai.data.local.LocalChatRepository
import com.example.mylocalai.data.local.ChatSessionSummary
import com.example.mylocalai.model.ChatMessage
import com.example.mylocalai.model.ChatRequest
import com.example.mylocalai.model.ConnectionMode
import com.example.mylocalai.model.AsyncChatJobCreated
import com.example.mylocalai.model.AsyncChatJobStatus
import com.example.mylocalai.model.AsyncChatJobEventsPage
import com.example.mylocalai.model.AsyncChatJobEvent
import com.example.mylocalai.model.StreamChunk
import com.example.mylocalai.model.SystemStats
import com.example.mylocalai.model.RemoteFileEntry
import com.example.mylocalai.model.RemoteFileListing
import com.example.mylocalai.model.FileTransferRequest
import com.example.mylocalai.model.RemoteFileDownload
import com.example.mylocalai.model.CommandRequest
import com.example.mylocalai.model.CommandResponse
import com.example.mylocalai.model.JobDto
import com.example.mylocalai.model.JobEnvelope
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
import com.example.mylocalai.data.remote.RetrofitClient
import com.example.mylocalai.data.remote.JobResponseParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.RequestBody
import okhttp3.MediaType
import okio.BufferedSink
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class ChatRepository(context: Context) {
    private val localHistory = LocalChatRepository(context)

    fun getAllSessionsFlow() = localHistory.getAllSessionsFlow()
    fun searchSessionsFlow(query: String) = localHistory.searchSessionsFlow(query)
    suspend fun getAllSessions() = localHistory.getAllSessions()
    suspend fun getSessionState(baseUrl: String, sessionId: String) =
        service(baseUrl).getSessionState(sessionId)
    suspend fun createPlan(baseUrl: String, request: com.example.mylocalai.model.PlanRequest) =
        service(baseUrl).createPlan(request)
    suspend fun reviewFiles(baseUrl: String, request: com.example.mylocalai.model.ReviewRequest) =
        service(baseUrl).reviewFiles(request)
    suspend fun writeRemoteFile(baseUrl: String, request: com.example.mylocalai.model.FileWriteRequest) =
        service(baseUrl).writeRemoteFile(request).use { it.string() }
    suspend fun getSession(sessionId: String) = localHistory.getSession(sessionId)
    fun getMessagesFlow(sessionId: String) = localHistory.getMessagesFlow(sessionId)
    suspend fun createSession(sessionId: String, title: String = "New chat") = localHistory.createSession(sessionId, title)
    suspend fun renameSession(sessionId: String, title: String) = localHistory.renameSession(sessionId, title)
    suspend fun saveMessage(sessionId: String, message: ChatMessage) = localHistory.saveMessage(sessionId, message)
    suspend fun deleteSession(sessionId: String) = localHistory.deleteSession(sessionId)
    suspend fun deleteMessage(messageId: String) = localHistory.deleteMessage(messageId)
    suspend fun clearMessagesForSession(sessionId: String) = localHistory.clearMessagesForSession(sessionId)
    suspend fun clearMessagesForSessions(sessionIds: List<String>) = localHistory.clearMessagesForSessions(sessionIds)
    suspend fun resetServerSession(baseUrl: String, sessionId: String): SessionResetResponse =
        service(baseUrl).resetSession(sessionId)

    private fun service(baseUrl: String) = RetrofitClient.createApiService(baseUrl)

    suspend fun requestAgentChat(baseUrl: String, request: ChatRequest, idempotencyKey: String) = service(baseUrl).chat(idempotencyKey, request)
    suspend fun createAgentChatJob(baseUrl: String, request: ChatRequest, idempotencyKey: String): AsyncChatJobCreated =
        service(baseUrl).createAgentChatJob(idempotencyKey, request)
    suspend fun getAgentChatJob(baseUrl: String, requestId: String): AsyncChatJobStatus =
        service(baseUrl).getAgentChatJob(requestId)
    suspend fun getAgentChatJobEvents(baseUrl: String, requestId: String, afterId: Long): AsyncChatJobEventsPage =
        service(baseUrl).getAgentChatJobEvents(requestId, afterId)
    suspend fun streamAgentChatJobEvents(
        baseUrl: String,
        requestId: String,
        afterId: Long = 0,
        onEvent: (AsyncChatJobEvent) -> Unit,
        onTerminalStatus: (String) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanUrl = baseUrl.trim().removeSuffix("/")
        val request = Request.Builder()
            .url("$cleanUrl/api/chat/jobs/$requestId/events/stream?after_id=$afterId")
            .get()
            .build()
        try {
            sseClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use
                val body = response.body ?: return@use
                val source = body.source()
                var eventType = ""
                var terminalSeen = false
                val dataLines = mutableListOf<String>()

                fun dispatch() {
                    val data = dataLines.joinToString("\n").trim()
                    dataLines.clear()
                    if (data.isBlank()) return
                    if (eventType == "status") {
                        // The job is finished: stop the stream.
                        terminalSeen = true
                        val status = runCatching {
                            JSONObject(data).optString("status").takeIf { it.isNotBlank() }
                        }.getOrNull()
                        onTerminalStatus(status ?: "running")
                        return
                    }
                    val parsed = runCatching { JSONObject(data) }.getOrNull()
                    val event = parsed?.let { json ->
                        AsyncChatJobEvent(
                            event_id = json.optLong("event_id", 0),
                            event_type = json.optString("event_type", eventType).ifBlank { eventType },
                            message = json.optString("message", ""),
                            created_at = json.optString("created_at", ""),
                        )
                    }
                    if (event != null) onEvent(event)
                }

                while (!terminalSeen && !source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    when {
                        line.isEmpty() -> dispatch()
                        line.startsWith("event:") -> eventType = line.removePrefix("event:").trim()
                        line.startsWith("data:") -> dataLines.add(line.removePrefix("data:").trim())
                    }
                }
                if (!terminalSeen) dispatch()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    suspend fun cancelAgentChatJob(baseUrl: String, requestId: String): String =
        service(baseUrl).cancelAgentChatJob(requestId).use { it.string() }
    suspend fun runCommand(baseUrl: String, request: CommandRequest): CommandResponse = service(baseUrl).runCommand(request)
    suspend fun startJob(baseUrl: String, request: CommandRequest): JobDto =
        service(baseUrl).startJob(request).job ?: throw java.io.IOException("The bridge did not return a job record.")
    suspend fun getJobs(baseUrl: String): List<JobDto> = service(baseUrl).getJobs(50).jobs
    suspend fun getJob(baseUrl: String, jobId: String): JobDto =
        service(baseUrl).getJob(jobId).use { body ->
            JobResponseParser.parse(body.string()) ?: throw java.io.IOException("The bridge returned an invalid job status response.")
        }
    suspend fun cancelJob(baseUrl: String, jobId: String): String = service(baseUrl).cancelJob(jobId).use { it.string() }
    suspend fun searchWeb(baseUrl: String, query: String): SearchResponse = service(baseUrl).search(query, 5)
    suspend fun getProcesses(baseUrl: String): String = service(baseUrl).getProcesses().use { it.string() }
    suspend fun serviceAction(baseUrl: String, request: ServiceRequest): String = service(baseUrl).serviceAction(request).use { it.string() }
    suspend fun packageAction(baseUrl: String, request: PackageRequest): String = service(baseUrl).packageAction(request).use { it.string() }
    suspend fun scheduleAction(baseUrl: String, request: ScheduleRequest): String = service(baseUrl).scheduleAction(request).use { it.string() }
    suspend fun getApps(baseUrl: String, query: String): String = service(baseUrl).getApps(query).use { it.string() }
    suspend fun launchApp(baseUrl: String, request: AppLaunchRequest): String = service(baseUrl).launchApp(request).use { it.string() }
    suspend fun desktopAction(baseUrl: String, request: DesktopRequest): String = service(baseUrl).desktopAction(request).use { it.string() }
    suspend fun desktopDiagnostics(baseUrl: String): String = service(baseUrl).desktopDiagnostics().use { it.string() }
    suspend fun getHealth(baseUrl: String): String = service(baseUrl).getHealth().use { it.string() }
    suspend fun getLogs(baseUrl: String, lines: Int = 200): String = service(baseUrl).getLogs(lines.coerceIn(1, 200)).use { it.string() }
    suspend fun getTags(baseUrl: String, refresh: Boolean = false): String = service(baseUrl).getTags(refresh).use { it.string() }
    suspend fun getBridgeHelp(baseUrl: String): String = service(baseUrl).getBridgeHelp().use { it.string() }
    suspend fun getAgentNotes(baseUrl: String, afterId: Long): AgentNotesResponse = service(baseUrl).getAgentNotes(limit = 50, afterId = afterId)
    suspend fun postAgentNote(baseUrl: String, request: AgentNoteRequest): String = service(baseUrl).postAgentNote(request).use { it.string() }
    suspend fun createSoftwareTask(baseUrl: String, key: String, request: SoftwareTaskRequest): String =
        service(baseUrl).createSoftwareTask(key, request).use { it.string() }
    suspend fun getSoftwareTask(baseUrl: String, taskId: String, statusUrl: String? = null): String =
        (if (statusUrl.isNullOrBlank()) service(baseUrl).getSoftwareTask(taskId) else service(baseUrl).getSoftwareTaskAt(statusUrl)).use { it.string() }
    suspend fun getSoftwareTaskEvents(baseUrl: String, taskId: String, afterId: Long, eventsUrl: String? = null): String =
        (if (eventsUrl.isNullOrBlank()) service(baseUrl).getSoftwareTaskEvents(taskId, afterId) else service(baseUrl).getSoftwareTaskEventsAt(eventsUrl, afterId)).use { it.string() }
    suspend fun resumeSoftwareTask(baseUrl: String, taskId: String, request: SoftwareTaskResumeRequest): String =
        service(baseUrl).resumeSoftwareTask(taskId, request).use { it.string() }
    suspend fun cancelSoftwareTask(baseUrl: String, taskId: String): String =
        service(baseUrl).cancelSoftwareTask(taskId).use { it.string() }
    suspend fun getSoftwareTaskDiff(baseUrl: String, taskId: String, diffUrl: String? = null): String =
        (if (diffUrl.isNullOrBlank()) service(baseUrl).getSoftwareTaskDiff(taskId) else service(baseUrl).getSoftwareTaskDiffAt(diffUrl)).use { it.string() }
    suspend fun deleteSoftwareTaskWorktree(baseUrl: String, taskId: String): String =
        service(baseUrl).deleteSoftwareTaskWorktree(taskId, confirm = true).use { it.string() }

    suspend fun listRemoteFiles(baseUrl: String, path: String, asRoot: Boolean = false): Result<RemoteFileListing> = withContext(Dispatchers.IO) {
        runCatching {
            // The bridge exposes GET /api/files for whole-filesystem browsing.
            val raw = service(baseUrl).listFiles(path, limit = 500, asRoot = asRoot).use { it.string() }
            val root = runCatching { JSONObject(raw) }.getOrNull()
                ?: throw java.io.IOException("The server returned an invalid directory listing.")
            val detail = listOf("detail", "message", "error").firstNotNullOfOrNull { key ->
                root.optString(key).takeIf { it.isNotBlank() && it != "null" }
            }
            if (!detail.isNullOrBlank()) throw java.io.IOException(detail)
            val array = root.optJSONArray("entries") ?: org.json.JSONArray()
            val entries = buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val name = item.optString("name").takeIf(String::isNotBlank) ?: continue
                    val entryPath = item.optString("path").takeIf(String::isNotBlank)
                        ?: "$path/$name".replace("//", "/")
                    val kind = item.optString("kind")
                    val isDirectory = kind.equals("dir", true) || item.optBoolean("is_dir") ||
                        kind.equals("directory", true)
                    add(
                        RemoteFileEntry(
                            name = name,
                            path = entryPath,
                            isDirectory = isDirectory,
                            isSymlink = item.optBoolean("is_symlink"),
                            size = item.optLong("size", -1).takeIf { it >= 0 },
                            modified = item.optString("modified").takeIf(String::isNotBlank),
                        )
                    )
                }
            }
            RemoteFileListing(
                entries = entries,
                truncated = root.optBoolean("truncated"),
                elevated = root.optBoolean("elevated"),
            )
        }
    }

    suspend fun transferRemoteFile(
        baseUrl: String,
        source: String,
        destination: String,
        operation: String,
        asRoot: Boolean = false,
        confirm: Boolean = true,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            service(baseUrl).transferFile(
                FileTransferRequest(source, destination, operation, asRoot, confirm)
            ).use { it.string() }
        }
    }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"

    private fun responseDetail(raw: String): String {
        if (raw.isBlank()) return "The server returned no additional detail."
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return raw
        return listOf("detail", "message", "error").firstNotNullOfOrNull { key ->
            json.optString(key).takeIf { it.isNotBlank() && it != "null" }
        } ?: raw
    }

    fun streamSse(baseUrl: String, request: ChatRequest): Flow<StreamChunk> = callbackFlow {
        val service = RetrofitClient.createApiService(baseUrl)
        val call = service.streamChat(request)
        val readerJob = launch(Dispatchers.IO) {
            try {   
                val response = call.execute()
                if (response.code() == 404) {
                    response.raw().close()
                    val fallback = service.chat(request)
                    trySend(StreamChunk(content = fallback.assistant_text ?: fallback.message?.content.orEmpty(), isDone = true))
                    close()
                    return@launch
                }
                if (!response.isSuccessful) {
                    val errorText = response.errorBody()?.string().orEmpty()
                    response.raw().close()
                    throw java.io.IOException("Chat stream failed (${response.code()}): $errorText")
                }

                response.body()?.use { body ->
                    val source = body.source()
                    var eventData = StringBuilder()
                    var streamDone = false

                    fun emitEvent() {
                        val payload = eventData.toString().trim()
                        eventData = StringBuilder()
                        if (payload.isBlank()) return
                        if (payload == "[DONE]") {
                            streamDone = true
                            trySend(StreamChunk(isDone = true))
                            return
                        }

                        val parsed = runCatching { JSONObject(payload) }.getOrNull()
                        if (parsed == null) {
                            trySend(StreamChunk(content = payload))
                            return
                        }
                        val choices = parsed.optJSONArray("choices")
                        val delta = choices?.optJSONObject(0)?.optJSONObject("delta")
                        val message = parsed.optJSONObject("message")
                        val content = when {
                            delta != null -> delta.optString("content", "")
                            message != null -> message.optString("content", "")
                            parsed.has("token") -> parsed.optString("token", "")
                            parsed.has("content") -> parsed.optString("content", "")
                            parsed.has("response") -> parsed.optString("response", "")
                            else -> ""
                        }
                        val eventType = parsed.optString("type", parsed.optString("event", ""))
                        val eventData = parsed.optString("data", "")
                        val stdout = parsed.optString("stdout", "")
                            .ifBlank { eventData.takeIf { eventType.equals("stdout", true) }.orEmpty() }
                            .takeIf(String::isNotBlank)
                        val stderr = parsed.optString("stderr", "")
                            .ifBlank { eventData.takeIf { eventType.equals("stderr", true) }.orEmpty() }
                            .takeIf(String::isNotBlank)
                        val exitCode = when {
                            parsed.has("exit_code") -> parsed.optInt("exit_code")
                            parsed.has("returncode") -> parsed.optInt("returncode")
                            else -> null
                        }
                        val output = buildString {
                            if (stdout != null) append("stdout:\n").append(stdout)
                            if (stderr != null) {
                                if (isNotEmpty()) append('\n')
                                append("stderr:\n").append(stderr)
                            }
                            if (exitCode != null) {
                                if (isNotEmpty()) append('\n')
                                append("exit_code: ").append(exitCode)
                            }
                        }
                        streamDone = parsed.optBoolean("done", false)
                        trySend(
                            StreamChunk(
                                content = content,
                                actionSummary = output,
                                isDone = streamDone
                            )
                        )
                    }

                    while (!source.exhausted() && !streamDone && isActive) {
                        val line = source.readUtf8Line() ?: break
                        when {
                            line.isEmpty() -> emitEvent()
                            line.startsWith("data:") -> {
                                if (eventData.isNotEmpty()) eventData.append('\n')
                                eventData.append(line.removePrefix("data:").trimStart())
                            }
                        }
                    }
                    emitEvent()
                }
                trySend(StreamChunk(isDone = true))
                close()
            } catch (error: Exception) {
                if (isActive) close(error)
            }
        }
        awaitClose {
            call.cancel()
            readerJob.cancel()
        }
    }

    // 20-minute timeouts (1200 seconds) & 15-minute connection keep-alive pool for heavy multi-step agent tool loops
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(1200, TimeUnit.SECONDS)
        .readTimeout(1200, TimeUnit.SECONDS)
        .writeTimeout(1200, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(5, 15, TimeUnit.MINUTES))
        .retryOnConnectionFailure(false)
        .build()

    // SSE needs fast failure detection so the app can reconnect with backoff.
    private val sseClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun fetchModels(baseUrl: String, refresh: Boolean = false): Result<List<String>> = withContext(Dispatchers.IO) {
        val cleanUrl = baseUrl.trim().removeSuffix("/")
        val request = Request.Builder()
            .url("$cleanUrl/api/tags?refresh=$refresh")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP Error: ${response.code}"))
                }

                val bodyString = response.body?.string()
                    ?: return@withContext Result.failure(Exception("Empty response body"))

                val json = JSONObject(bodyString)
                val modelsArray = json.optJSONArray("models") ?: JSONArray()
                val modelNames = mutableListOf<String>()

                for (i in 0 until modelsArray.length()) {
                    val modelObj = modelsArray.optJSONObject(i)
                    val name = if (modelObj != null) {
                        modelObj.optString("name", "").ifBlank { modelObj.optString("model", "") }
                    } else {
                        modelsArray.optString(i, "")
                    }
                    if (name.isNotBlank() && name !in modelNames) {
                        modelNames.add(name)
                    }
                }

                if (modelNames.isEmpty()) {
                    Result.success(listOf("qwen2.5-coder:1.5b", "qwen3.5:4b", "llama3.2:3b"))
                } else {
                    Result.success(modelNames)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadFile(
        baseUrl: String,
        fileName: String,
        fileBytes: ByteArray,
        mimeType: String,
        destination: String = "workspace",
        asRoot: Boolean = false,
        confirm: Boolean = false,
        onProgress: (sentBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanUrl = baseUrl.trim().removeSuffix("/")
        val mediaType = (if (mimeType.isNotBlank()) mimeType else "application/octet-stream").toMediaTypeOrNull()

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                fileName,
                ProgressRequestBody(fileBytes, mediaType, onProgress)
            )
            .addFormDataPart("destination", destination)
            .build()

        val request = Request.Builder()
            .url("$cleanUrl/api/upload?destination=${URLEncoder.encode(destination, "UTF-8")}&as_root=$asRoot&confirm=$confirm")
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    val detail = responseDetail(errBody)
                    val prefix = if (response.code == 413) "Upload size limit (HTTP 413)" else "Upload Error ${response.code}"
                    return@withContext Result.failure(Exception("$prefix: $detail"))
                }
                val respBody = response.body?.string() ?: "File uploaded successfully"
                Result.success(respBody)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchSystemStats(baseUrl: String): Result<SystemStats> = withContext(Dispatchers.IO) {
        val cleanUrl = baseUrl.trim().removeSuffix("/")
        val request = Request.Builder()
            .url("$cleanUrl/api/stats")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP Error: ${response.code}"))
                }
                val bodyString = response.body?.string()
                    ?: return@withContext Result.failure(Exception("Empty stats response"))

                val json = JSONObject(bodyString)
                val stats = SystemStats(
                    cpuUsage = json.optString("cpu_usage", json.optString("cpu", "N/A")),
                    uptime = json.optString("uptime", "N/A"),
                    ramUsed = json.optString("ram_used", json.optString("ram", json.optString("memory", "N/A"))),
                    ramTotal = json.optString("ram_total", "N/A"),
                    ramPercent = json.optString("ram_percent", "N/A"),
                    diskUsed = json.optString("disk_used", json.optString("disk", "N/A")),
                    diskTotal = json.optString("disk_total", "N/A"),
                    diskPercent = json.optString("disk_percent", "N/A")
                )
                Result.success(stats)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchServerLogs(baseUrl: String, lines: Int = 200): Result<String> = withContext(Dispatchers.IO) {
        val cleanUrl = baseUrl.trim().removeSuffix("/")
        val request = Request.Builder()
            .url("$cleanUrl/api/logs?lines=$lines")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    return@withContext Result.failure(Exception("Logs Error ${response.code}: $errBody"))
                }
                val logs = response.body?.string() ?: "No server logs available."
                Result.success(logs)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadFile(
        baseUrl: String,
        filePath: String,
        onProgress: (receivedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Result<RemoteFileDownload> = withContext(Dispatchers.IO) {
        val cleanUrl = baseUrl.trim().removeSuffix("/")
        val encodedPath = URLEncoder.encode(filePath, "UTF-8")
        val request = Request.Builder()
            .url("$cleanUrl/api/download?path=$encodedPath")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    return@withContext Result.failure(Exception("Download Error ${response.code}: $errBody"))
                }
                val body = response.body ?: return@withContext Result.failure(Exception("The download response was empty."))
                val contentLength = body.contentLength()
                if (contentLength > MAX_PREVIEW_BYTES) {
                    return@withContext Result.failure(IllegalArgumentException("This file is too large to preview (${contentLength} bytes)."))
                }
                val bytes = body.byteStream().use { input ->
                    val output = ByteArrayOutputStream(minOf(contentLength.coerceAtLeast(0L), MAX_PREVIEW_BYTES).toInt())
                    val chunk = ByteArray(8 * 1024)
                    var totalBytes = 0L
                    while (true) {
                        val read = input.read(chunk)
                        if (read < 0) break
                        totalBytes += read
                        onProgress(totalBytes, contentLength)
                        if (totalBytes > MAX_PREVIEW_BYTES) {
                            throw IllegalArgumentException("This file is too large to preview (limit is 8 MiB).")
                        }
                        output.write(chunk, 0, read)
                    }
                    output.toByteArray()
                }
                val extension = filePath.substringAfterLast('.', "").lowercase()
                val declaredType = body.contentType()?.let { contentType ->
                    "${contentType.type}/${contentType.subtype}"
                }.orEmpty()
                val mimeType = declaredType.takeIf { it != "application/octet-stream" }
                    ?: extensionMimeType(extension)
                val imageBytes = bytes.takeIf { mimeType.startsWith("image/") && mimeType != "image/svg+xml" }
                val textFile = mimeType.startsWith("text/") || mimeType in TEXT_MIME_TYPES
                val looksBinary = bytes.any { it == 0.toByte() }
                val content = if (textFile && !looksBinary) String(bytes, Charsets.UTF_8) else ""
                Result.success(RemoteFileDownload(content, mimeType, imageBytes, bytes.size))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadFileBytes(
        baseUrl: String,
        filePath: String,
        asRoot: Boolean = false,
        onProgress: (receivedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        val cleanUrl = baseUrl.trim().removeSuffix("/")
        val encodedPath = URLEncoder.encode(filePath, "UTF-8")
        val request = Request.Builder()
            .url("$cleanUrl/api/download?path=$encodedPath&as_root=$asRoot")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    return@withContext Result.failure(Exception("Download Error ${response.code}: $errBody"))
                }
                val body = response.body ?: return@withContext Result.failure(Exception("The download response was empty."))
                val contentLength = body.contentLength()
                val bytes = body.byteStream().use { input ->
                    val output = ByteArrayOutputStream()
                    val chunk = ByteArray(16 * 1024)
                    var totalBytes = 0L
                    while (true) {
                        val read = input.read(chunk)
                        if (read < 0) break
                        totalBytes += read
                        output.write(chunk, 0, read)
                        onProgress(totalBytes, contentLength)
                    }
                    output.toByteArray()
                }
                Result.success(bytes)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extensionMimeType(extension: String): String = when (extension) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "svg" -> "image/svg+xml"
        "json" -> "application/json"
        "xml" -> "application/xml"
        "pdf" -> "application/pdf"
        "txt", "log", "md", "sh", "py", "kt", "java", "yaml", "yml", "toml", "csv", "conf", "ini", "cfg", "service", "html", "css", "js", "ts", "sql" -> "text/plain"
        else -> "application/octet-stream"
    }

    private companion object {
        const val MAX_PREVIEW_BYTES = 8 * 1024 * 1024L
        val TEXT_MIME_TYPES = setOf("application/json", "application/xml", "image/svg+xml")
    }

    private class ProgressRequestBody(
        private val bytes: ByteArray,
        private val mediaType: MediaType?,
        private val onProgress: (Long, Long) -> Unit,
    ) : RequestBody() {
        override fun contentType(): MediaType? = mediaType
        override fun contentLength(): Long = bytes.size.toLong()
        override fun writeTo(sink: BufferedSink) {
            var offset = 0
            val chunkSize = 8 * 1024
            while (offset < bytes.size) {
                val count = minOf(chunkSize, bytes.size - offset)
                sink.write(bytes, offset, count)
                offset += count
                onProgress(offset.toLong(), bytes.size.toLong())
            }
        }
    }

    suspend fun deleteFile(baseUrl: String, filePath: String, asRoot: Boolean = false): Result<String> = withContext(Dispatchers.IO) {
        val cleanUrl = baseUrl.trim().removeSuffix("/")
        val encodedPath = URLEncoder.encode(filePath, "UTF-8")
        val request = Request.Builder()
            .url("$cleanUrl/api/file?path=$encodedPath&as_root=$asRoot")
            .delete()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    return@withContext Result.failure(Exception("Delete Error ${response.code}: $errBody"))
                }
                val resultMsg = response.body?.string() ?: "File deleted successfully"
                Result.success(resultMsg)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun streamChat(
        baseUrl: String,
        model: String,
        systemPrompt: String,
        history: List<ChatMessage>,
        mode: ConnectionMode
    ): Flow<StreamChunk> = flow {
        val cleanUrl = baseUrl.trim().removeSuffix("/")
        val messagesJsonArray = JSONArray()

        if (systemPrompt.isNotBlank()) {
            val systemObj = JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            }
            messagesJsonArray.put(systemObj)
        }

        for (msg in history) {
            val msgObj = JSONObject().apply {
                put("role", msg.role)
                put("content", msg.content)
            }
            messagesJsonArray.put(msgObj)
        }

        val requestPayload = JSONObject().apply {
            put("model", model)
            put("messages", messagesJsonArray)
            put("stream", true)
            if (mode == ConnectionMode.AGENT) {
                put("mode", "agent")
            }
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = requestPayload.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url("$cleanUrl/api/chat")
            .post(requestBody)
            .build()

        val call = client.newCall(request)

        try {
            val response = call.execute()
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: ""
                emit(StreamChunk(content = "\n[Error ${response.code}: $errBody]", isDone = true))
                response.close()
                return@flow
            }

            val body = response.body
            if (body == null) {
                emit(StreamChunk(content = "\n[Error: Empty response body]", isDone = true))
                return@flow
            }

            val inputStream = body.byteStream()
            val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))

            var line: String? = reader.readLine()
            while (line != null) {
                if (line.isNotBlank()) {
                    val json = runCatching { JSONObject(line) }.getOrNull()
                    if (json == null) {
                        emit(StreamChunk(content = line))
                    } else {
                        val message = json.optJSONObject("message")
                        val content = when {
                            message?.has("content") == true -> message.optString("content", "")
                            json.has("response") -> json.optString("response", "")
                            else -> ""
                        }
                        val thinking = message?.optString("thinking", "").orEmpty()
                        val toolCalls = message?.optJSONArray("tool_calls")
                        val actions = buildList {
                            if (toolCalls != null) for (index in 0 until toolCalls.length()) {
                                val toolCall = toolCalls.optJSONObject(index) ?: continue
                                val function = toolCall.optJSONObject("function")
                                val name = function?.optString("name").orEmpty()
                                if (name.isNotBlank()) add("Tool: $name")
                            }
                        }
                        val done = json.optBoolean("done", false)
                        if (content.isNotEmpty() || thinking.isNotEmpty() || actions.isNotEmpty() || done) {
                            emit(
                                StreamChunk(
                                    content = content,
                                    thinking = thinking,
                                    actionSummary = actions.joinToString("\n"),
                                    isDone = done
                                )
                            )
                        }
                        if (done) break
                    }
                }
                line = reader.readLine()
            }
            reader.close()
            body.close()
        } catch (e: Exception) {
            emit(StreamChunk(content = "\n[Connection Error] ${e.localizedMessage ?: e.javaClass.simpleName}", isDone = true))
        }
    }.flowOn(Dispatchers.IO)
}
