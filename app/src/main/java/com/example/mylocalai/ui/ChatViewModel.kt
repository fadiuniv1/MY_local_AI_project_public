package com.example.mylocalai.ui

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import java.io.File
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mylocalai.data.ChatRepository
import com.example.mylocalai.data.PreferencesRepository
import com.example.mylocalai.data.local.ChatSessionSummary
import com.example.mylocalai.model.ChatMessage
import com.example.mylocalai.model.ChatResponse
import com.example.mylocalai.model.ConnectionMode
import com.example.mylocalai.model.FilePreviewState
import com.example.mylocalai.model.SystemStats
import com.example.mylocalai.model.RemoteFileEntry
import com.example.mylocalai.model.CommandRequest
import com.example.mylocalai.model.JobDto
import com.example.mylocalai.model.ToolResultDto
import com.example.mylocalai.model.AsyncChatJobCreated
import com.example.mylocalai.model.AsyncChatJobEvent
import com.example.mylocalai.model.ServerHealthMetrics
import com.example.mylocalai.model.PersistedChatJob
import com.example.mylocalai.MyLocalAIApplication
import com.example.mylocalai.util.AgentAnswerSanitizer
import com.example.mylocalai.worker.ChatJobCompletionMonitor
import com.example.mylocalai.model.SearchResponse
import com.example.mylocalai.model.ServiceRequest
import com.example.mylocalai.model.PackageRequest
import com.example.mylocalai.model.ScheduleRequest
import com.example.mylocalai.model.AppLaunchRequest
import com.example.mylocalai.model.DesktopRequest
import com.example.mylocalai.model.BridgeAuditEntry
import com.example.mylocalai.model.AgentNoteDto
import com.example.mylocalai.model.AgentNoteRequest
import com.example.mylocalai.model.SoftwareTaskRequest
import com.example.mylocalai.model.SoftwareTaskResumeRequest
import com.example.mylocalai.model.SoftwareTaskSummary
import com.example.mylocalai.model.SoftwareTaskEvent
import com.example.mylocalai.model.SoftwareTaskDiff
import com.example.mylocalai.model.TrackedSoftwareTask
import com.example.mylocalai.model.PendingSoftwareTaskCreate
import com.example.mylocalai.model.PendingAgentChatRequest
import com.example.mylocalai.model.QueuedPromptData
import com.example.mylocalai.model.QueuedPromptInfo
import com.example.mylocalai.model.SessionTaskInfo
import com.example.mylocalai.model.PlanRequest
import com.example.mylocalai.model.PlanResponse
import com.example.mylocalai.model.ReviewRequest
import com.example.mylocalai.model.FileWriteRequest
import com.example.mylocalai.util.LocaleHelper
import com.example.mylocalai.R
import com.example.mylocalai.model.SoftwareTaskOffer
import com.example.mylocalai.data.remote.SoftwareTaskParser
import com.example.mylocalai.data.remote.ApiError
import com.example.mylocalai.worker.JobCompletionMonitor
import com.example.mylocalai.worker.SoftwareTaskMonitor
import com.example.mylocalai.notification.AgentNotifications
import com.example.mylocalai.notification.AppVisibility
import com.google.gson.JsonObject
import retrofit2.HttpException
import org.json.JSONObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.security.MessageDigest
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private val ACTIVE_JOB_STATUSES = setOf("queued", "running")

private fun Throwable.displayMessage(fallback: String): String =
    message?.trim()?.takeIf { it.isNotEmpty() } ?: javaClass.simpleName.takeIf { it.isNotBlank() } ?: fallback

data class ChatUiState(
    val sessionId: String = "",
    val sessions: List<ChatSessionSummary> = emptyList(),
    val sessionSearchQuery: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val availableModels: List<String> = listOf("qwen2.5-coder:1.5b", "qwen3.5:4b", "llama3.2:3b"),
    val selectedModel: String = "qwen2.5-coder:1.5b",
    val terminalTheme: String = "Dracula",
    val connectionMode: ConnectionMode = ConnectionMode.AGENT,
    val agentBaseUrl: String = PreferencesRepository.DEFAULT_AGENT_BASE_URL,
    val pureChatBaseUrl: String = PreferencesRepository.DEFAULT_PURE_CHAT_BASE_URL,
    val isConnected: Boolean = false,
    val isFetchingModels: Boolean = false,
    val isStreaming: Boolean = false,
    val queuedCount: Int = 0,
    val queuedPrompts: List<QueuedPromptInfo> = emptyList(),
    val showQueueDialog: Boolean = false,
    val notifyReplies: Boolean = true,
    val notifyDownloads: Boolean = true,
    val notifyTasks: Boolean = true,
    val sessionTaskInfo: Map<String, SessionTaskInfo> = emptyMap(),
    val planPreview: PlanResponse? = null,
    val showPlanDialog: Boolean = false,
    val reviewFindings: String? = null,
    val showReviewDialog: Boolean = false,
    val language: String = LocaleHelper.LANG_SYSTEM,
    val allowedTools: Set<String> = setOf(
        "read_file", "list_files", "write_file", "run_command", "transfer_file",
        "web_search", "research", "fetch_url", "get_system_status", "network_diagnostics",
    ),
    val isUploading: Boolean = false,
    val uploadProgress: Float? = null,
    val canRetryUpload: Boolean = false,
    val inputText: String = "",
    val pendingChatImages: List<String> = emptyList(),
    val systemPrompt: String = PreferencesRepository.DEFAULT_SYSTEM_PROMPT,
    val showSettingsDialog: Boolean = false,
    val showStatsDialog: Boolean = false,
    val showServerLogsDialog: Boolean = false,
    val serverLogsLines: Int = 200,
    val serverLogs: String? = null,
    val isFetchingLogs: Boolean = false,
    val serverLogsError: String? = null,
    val serverLogsSaveMessage: String? = null,
    val serverLogsSaveError: String? = null,
    val isDownloadingRemoteFile: Boolean = false,
    val downloadingFileName: String? = null,
    val fileDownloadMessage: String? = null,
    val fileDownloadError: String? = null,
    val systemStats: SystemStats? = null,
    val isFetchingStats: Boolean = false,
    val serverHealthMetrics: ServerHealthMetrics? = null,
    val serverMetricsLoading: Boolean = false,
    val serverMetricsError: String? = null,
    val activeChatJobElapsedSeconds: Long? = null,
    val lastChatJobId: String? = null,
    val forceLegacyChat: Boolean = false,
    val delegateLongWork: Boolean = false,
    val isOnline: Boolean = true,
    val isAwaitingNetwork: Boolean = false,
    val filePreviewState: FilePreviewState? = null,
    val showRemoteFilesDialog: Boolean = false,
    val remoteFiles: List<RemoteFileEntry> = emptyList(),
    val remoteFilesPath: String = "~/agent-bridge",
    val isLoadingRemoteFiles: Boolean = false,
    val remoteFilesError: String? = null,
    val remoteFilesTruncated: Boolean = false,
    val remoteFilesAsRoot: Boolean = false,
    val remoteFilesElevated: Boolean = false,
    val jobs: List<JobDto> = emptyList(),
    val showJobsDialog: Boolean = false,
    val jobsLoading: Boolean = false,
    val isStartingJob: Boolean = false,
    val jobsError: String? = null,
    val retryableJobCommand: String? = null,
    val jobRetrySecondsRemaining: Int? = null,
    val jobRetryDelaySeconds: Int = 10,
    val bridgeRoutes: Set<String>? = null,
    val showSoftwareTasksDialog: Boolean = false,
    val softwareTasksRestoring: Boolean = true,
    val softwareTasks: List<SoftwareTaskSummary> = emptyList(),
    val selectedSoftwareTaskId: String? = null,
    val softwareTaskEvents: List<SoftwareTaskEvent> = emptyList(),
    val softwareTaskLoading: Boolean = false,
    val softwareTaskCreating: Boolean = false,
    val softwareTaskError: String? = null,
    val pendingSoftwareTaskCreate: PendingSoftwareTaskCreate? = null,
    val agentChatRetryKey: String? = null,
    val agentChatRetryBaseUrl: String? = null,
    val softwareTaskDiff: SoftwareTaskDiff? = null,
    val softwareTaskDiffLoading: Boolean = false,
    val softwareTaskDeleteConfirmation: String? = null,
    val showHealthDialog: Boolean = false,
    val healthLoading: Boolean = false,
    val healthResult: String? = null,
    val healthError: String? = null,
    val showBridgeToolsDialog: Boolean = false,
    val bridgeToolsBusy: Boolean = false,
    val bridgeToolsResult: String? = null,
    val searchResults: SearchResponse? = null,
    val bridgeAuditEntries: List<BridgeAuditEntry> = emptyList(),
    val showAuditHistoryDialog: Boolean = false,
    val showAgentNotesDialog: Boolean = false,
    val agentNotes: List<AgentNoteDto> = emptyList(),
    val notesLoading: Boolean = false,
    val notesPosting: Boolean = false,
    val notesError: String? = null,
    val notesDraft: String = "",
    val bridgeHelp: String? = null,
    val visionCapableModels: Set<String> = emptySet(),
    val isClearingChat: Boolean = false,
    val clearChatError: String? = null,
    val failedClearSessionId: String? = null,
    val failedClearSessionIds: List<String> = emptyList(),
    val showClearConversationDialog: Boolean = false,
    val showClearAllChatsDialog: Boolean = false,
    val errorMessage: String? = null
) {
    val activeSessionId: String get() = sessionId
    val currentMessages: List<ChatMessage> get() = messages
    val activeBaseUrl: String
        get() = if (connectionMode == ConnectionMode.AGENT) agentBaseUrl else pureChatBaseUrl
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val remoteWorkspaceRoot = "~/agent-bridge"

    private val preferencesRepository = PreferencesRepository(application)
    private val chatRepository = ChatRepository(application)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private data class QueuedPrompt(
        val text: String,
        val images: List<String>,
        val userMsg: ChatMessage,
        val assistantId: String,
    )

    private val promptQueue = ArrayDeque<QueuedPrompt>()
    private fun persistQueue() {
        val session = _uiState.value.sessionId
        val items = promptQueue.map {
            QueuedPromptData(it.text, it.images, it.userMsg.id, it.assistantId, session)
        }
        viewModelScope.launch { runCatching { preferencesRepository.saveQueuedPrompts(items) } }
    }
    private fun syncQueueUi() {
        val items = promptQueue.mapIndexed { index, queued ->
            QueuedPromptInfo(queued.assistantId, queued.text, index + 1, queued.images.size)
        }
        _uiState.update { it.copy(queuedCount = items.size, queuedPrompts = items) }
    }

    fun openQueueDialog() { _uiState.update { it.copy(showQueueDialog = true) } }
    fun closeQueueDialog() { _uiState.update { it.copy(showQueueDialog = false) } }

    fun requestPlan(prompt: String) {
        if (prompt.isBlank()) return
        _uiState.update { it.copy(showPlanDialog = true, planPreview = null) }
        viewModelScope.launch {
            runCatching {
                val result = chatRepository.createPlan(
                    _uiState.value.agentBaseUrl,
                    PlanRequest(prompt, _uiState.value.sessionId),
                )
                _uiState.update { it.copy(planPreview = result) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(errorMessage = "Could not create plan: ${error.displayMessage("unknown error")}")
                }
            }
        }
    }

    fun closePlanDialog() { _uiState.update { it.copy(showPlanDialog = false) } }

    fun approvePlanAndSend() {
        val plan = _uiState.value.planPreview ?: return
        _uiState.update {
            it.copy(
                inputText = it.inputText.ifBlank {
                    "Follow this plan in ${plan.workspace.ifBlank { "the existing workspace" }}:\n${plan.plan}"
                },
                showPlanDialog = false,
            )
        }
    }

    fun reviewSessionFiles() {
        val session = _uiState.value.sessionId
        _uiState.update { it.copy(showReviewDialog = true, reviewFindings = "Reviewing files…") }
        viewModelScope.launch {
            runCatching {
                val result = chatRepository.reviewFiles(
                    _uiState.value.agentBaseUrl, ReviewRequest(session),
                )
                _uiState.update {
                    it.copy(reviewFindings = result.findings.ifBlank { "No findings returned." })
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(reviewFindings = "Review failed: ${error.displayMessage("unknown error")}")
                }
            }
        }
    }

    fun closeReviewDialog() { _uiState.update { it.copy(showReviewDialog = false) } }

    fun undoFileWrite(path: String, previousContent: String) {
        viewModelScope.launch {
            runCatching {
                chatRepository.writeRemoteFile(
                    _uiState.value.agentBaseUrl,
                    FileWriteRequest(path, previousContent),
                )
                _uiState.update { it.copy(fileDownloadMessage = "Restored previous content of $path") }
                refreshSessionTaskInfo(_uiState.value.sessionId)
            }.onFailure { error ->
                _uiState.update { it.copy(fileDownloadError = "Undo failed: ${error.displayMessage("unknown error")}") }
            }
        }
    }

    fun setLanguage(language: String) {
        LocaleHelper.setLanguage(getApplication(), language)
        _uiState.update { it.copy(language = language) }
    }

    fun setToolAllowed(name: String, allowed: Boolean) {
        val tools = if (allowed) _uiState.value.allowedTools + name else _uiState.value.allowedTools - name
        _uiState.update { it.copy(allowedTools = tools) }
        viewModelScope.launch { runCatching { preferencesRepository.saveAllowedTools(tools) } }
    }

    fun refreshSessionTaskInfo(sessionId: String) {
        if (sessionId.isBlank()) return
        viewModelScope.launch {
            runCatching {
                val baseUrl = _uiState.value.agentBaseUrl
                val dto = chatRepository.getSessionState(baseUrl, sessionId)
                val success = dto.lastSuccess
                val info = SessionTaskInfo(
                    sessionId = sessionId,
                    goal = (success?.goal ?: dto.goal).take(220),
                    workspace = (success?.workspace ?: dto.workspace).take(220),
                    files = (success?.files ?: dto.files).take(8),
                    lastSuccessAt = success?.at.orEmpty(),
                    status = if (dto.lastFailure.isNotBlank() && success == null) "Needs attention" else "On track",
                )
                _uiState.update { state ->
                    state.copy(sessionTaskInfo = state.sessionTaskInfo + (sessionId to info))
                }
            }
        }
    }

    fun continueLastTask(sessionId: String = _uiState.value.sessionId) {
        val info = _uiState.value.sessionTaskInfo[sessionId]
        val goal = info?.goal?.takeIf { it.isNotBlank() } ?: return
        val prompt = "$goal — continue and finish using the existing files in ${info.workspace}."
        _uiState.update { it.copy(inputText = prompt) }
        sendMessage()
    }

    fun setNotificationPrefs(replies: Boolean, downloads: Boolean, tasks: Boolean) {
        _uiState.update {
            it.copy(notifyReplies = replies, notifyDownloads = downloads, notifyTasks = tasks)
        }
        viewModelScope.launch {
            runCatching { preferencesRepository.saveNotificationPrefs(replies, downloads, tasks) }
        }
    }

    fun cancelQueuedPrompt(assistantId: String) {
        val removed = promptQueue.firstOrNull { it.assistantId == assistantId }
        if (removed == null) return
        promptQueue.remove(removed)
        _uiState.update { state ->
            state.copy(messages = state.messages.filterNot {
                it.id == removed.assistantId || it.id == removed.userMsg.id
            })
        }
        syncQueueUi()
        persistQueue()
    }

    fun moveQueuedPrompt(assistantId: String, delta: Int) {
        val index = promptQueue.indexOfFirst { it.assistantId == assistantId }
        val target = index + delta
        if (index < 0 || target < 0 || target >= promptQueue.size) return
        val item = promptQueue.removeAt(index)
        promptQueue.add(target, item)
        val positions = promptQueue.mapIndexed { i, queued -> queued.assistantId to i + 1 }.toMap()
        _uiState.update { state ->
            state.copy(messages = state.messages.map { msg ->
                if (positions.containsKey(msg.id)) msg.copy(queuePosition = positions[msg.id]) else msg
            })
        }
        syncQueueUi()
        persistQueue()
    }

    private var streamingJob: Job? = null
    private var activeAgentChatJobId: String? = null
    private var activeRequestGeneration: Long = 0L
    private val handledAgentRequestIds = LinkedHashSet<String>()
    private var healthCheckJob: Job? = null
    private var statsPollingJob: Job? = null
    private var sessionMessagesJob: Job? = null
    private var sessionSearchJob: Job? = null
    private var remoteFilesJob: Job? = null
    private var jobsPollingJob: Job? = null
    private var jobRetryJob: Job? = null
    private var softwareTaskPollingJob: Job? = null
    private val taskEventCursors = mutableMapOf<String, Long>()
    private val lastTaskNotifications = mutableMapOf<String, String>()
    private var lastUploadUri: Uri? = null
    private var lastDownloadPath: String? = null
    private var cachedModels: List<String> = emptyList()
    private var lastTagsFetchMs: Long = 0L

    init {
        updateSessionSearch("")
        viewModelScope.launch {
            val monitor = (getApplication<Application>() as? MyLocalAIApplication)?.networkMonitor ?: return@launch
            monitor.isOnline.collect { online ->
                _uiState.update { state ->
                    state.copy(isOnline = online, isAwaitingNetwork = if (online) false else state.isAwaitingNetwork)
                }
                if (online && streamingJob?.isActive != true) {
                    // A long PC job may have outlived the phone's tracker; reattach to it.
                    restoreActiveChatJob()
                }
            }
        }
        viewModelScope.launch { restoreSoftwareTasks() }
        viewModelScope.launch {
            preferencesRepository.bridgeAuditHistoryFlow.collect { entries ->
                _uiState.update { it.copy(bridgeAuditEntries = entries) }
            }
        }
        viewModelScope.launch {
            val savedMode = preferencesRepository.connectionModeFlow.first()
            val savedAgentUrl = preferencesRepository.agentBaseUrlFlow.first()
            val savedPureChatUrl = preferencesRepository.pureChatBaseUrlFlow.first()
            val savedSystemPrompt = preferencesRepository.systemPromptFlow.first()
            val savedSelectedModel = preferencesRepository.selectedModelFlow.first()
            val savedTerminalTheme = preferencesRepository.terminalThemeFlow.first()
            val savedActiveSessionId = preferencesRepository.activeSessionIdFlow.first()
            val savedForceLegacyChat = preferencesRepository.forceLegacyChatFlow.first()
            val savedDelegateLongWork = preferencesRepository.delegateLongWorkFlow.first()
            val savedNotifyReplies = preferencesRepository.notifyRepliesFlow.first()
            val savedNotifyDownloads = preferencesRepository.notifyDownloadsFlow.first()
            val savedNotifyTasks = preferencesRepository.notifyTasksFlow.first()
            val savedAllowedTools = preferencesRepository.allowedToolsFlow.first()
            val savedLanguage = LocaleHelper.getLanguage(getApplication())

            _uiState.update { state ->
                state.copy(
                    connectionMode = savedMode,
                    agentBaseUrl = savedAgentUrl,
                    pureChatBaseUrl = savedPureChatUrl,
                    systemPrompt = savedSystemPrompt,
                    selectedModel = savedSelectedModel,
                    terminalTheme = savedTerminalTheme,
                    forceLegacyChat = savedForceLegacyChat,
                    delegateLongWork = savedDelegateLongWork,
                    notifyReplies = savedNotifyReplies,
                    notifyDownloads = savedNotifyDownloads,
                    notifyTasks = savedNotifyTasks,
                    allowedTools = savedAllowedTools,
                    language = savedLanguage,
                )
            }

            val sessions = chatRepository.getAllSessions()
            val initialSessionId = sessions.firstOrNull { it.sessionId == savedActiveSessionId }?.sessionId
                ?: sessions.firstOrNull()?.sessionId
            if (initialSessionId != null) switchSession(initialSessionId) else createNewSession()
            restoreQueuedPrompts()

            fetchBridgeHelp()
            fetchModels()
            startPeriodicHealthCheck()
            restoreActiveChatJob()
        }
    }

    private fun startPeriodicHealthCheck() {
        healthCheckJob?.cancel()
        healthCheckJob = viewModelScope.launch {
            while (isActive) {
                delay(20000) // Periodic ping every 20 seconds
                if (!_uiState.value.isStreaming) {
                    val targetUrl = _uiState.value.activeBaseUrl
                    val result = chatRepository.fetchSystemStats(targetUrl)
                    _uiState.update { state ->
                        state.copy(isConnected = result.isSuccess)
                    }
                    // Refresh health too, so the low-RAM banner clears when memory recovers.
                    fetchServerMetrics()
                }
            }
        }
    }

    private fun start15sStatsPolling() {
        statsPollingJob?.cancel()
        statsPollingJob = viewModelScope.launch {
            while (isActive && _uiState.value.showStatsDialog) {
                fetchSystemStats()
                delay(15000) // Poll stats every 15 seconds
            }
        }
    }

    fun fetchModels(refresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isFetchingModels = true, errorMessage = null) }
            val targetUrl = _uiState.value.activeBaseUrl

            // Cache /api/tags for one minute; the explicit refresh action bypasses the cache.
            if (!refresh && cachedModels.isNotEmpty() && System.currentTimeMillis() - lastTagsFetchMs < 60_000L) {
                _uiState.update { it.copy(availableModels = cachedModels, isFetchingModels = false) }
                return@launch
            }

            val result = chatRepository.fetchModels(targetUrl, refresh)
            result.onSuccess { modelsList ->
                _uiState.update { state ->
                    val defaultFallback = listOf("qwen2.5-coder:1.5b", "qwen3.5:4b", "llama3.2:3b")
                    val finalModelsList = if (modelsList.isNotEmpty()) modelsList else defaultFallback
                    cachedModels = finalModelsList
                    lastTagsFetchMs = System.currentTimeMillis()

                    val defaultModel = PreferencesRepository.DEFAULT_SELECTED_MODEL
                    val preselected = when {
                        finalModelsList.contains("qwen3.5:4b") &&
                            (state.selectedModel == defaultModel || !finalModelsList.contains(state.selectedModel)) -> "qwen3.5:4b"
                        finalModelsList.contains(state.selectedModel) -> state.selectedModel
                        finalModelsList.contains("qwen2.5-coder:1.5b") -> "qwen2.5-coder:1.5b"
                        else -> finalModelsList.firstOrNull() ?: "qwen2.5-coder:1.5b"
                    }

                    state.copy(
                        availableModels = finalModelsList,
                        selectedModel = preselected,
                        isConnected = true,
                        isFetchingModels = false
                    )
                }
                preferencesRepository.saveSelectedModel(_uiState.value.selectedModel)
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(
                        isConnected = false,
                        isFetchingModels = false,
                        errorMessage = "Cannot connect to ${_uiState.value.connectionMode.name} at $targetUrl: ${error.localizedMessage}"
                    )
                }
            }
        }
    }

    fun refreshServerCapabilities() {
        fetchBridgeHelp()
        fetchModels(refresh = true)
    }

    fun fetchBridgeHelp() {
        _uiState.update { it.copy(bridgeHelp = null, bridgeRoutes = null, visionCapableModels = emptySet()) }
        viewModelScope.launch {
            runCatching { chatRepository.getBridgeHelp(_uiState.value.agentBaseUrl) }
                .onSuccess { raw ->
                    _uiState.update { it.copy(bridgeHelp = raw) }
                    runCatching {
                        val json = JSONObject(raw)
                        val models = json.optJSONObject("runtime_snapshot")
                            ?.optJSONObject("available_models")?.optJSONArray("models")
                        val names = mutableListOf<String>()
                        val vision = mutableSetOf<String>()
                        val routes = mutableSetOf<String>()
                        if (models != null) for (index in 0 until models.length()) {
                            val model = models.optJSONObject(index) ?: continue
                            val name = model.optString("name")
                            if (name.isNotBlank()) names.add(name)
                            val capabilities = model.optJSONArray("capabilities")
                            if (name.isNotBlank() && capabilities != null && (0 until capabilities.length()).any { capabilities.optString(it) == "vision" }) {
                                vision.add(name)
                            }
                        }
                        json.optJSONArray("api_endpoints")?.let { endpoints ->
                            for (index in 0 until endpoints.length()) {
                                val endpoint = endpoints.optJSONObject(index) ?: continue
                                val path = endpoint.optString("path")
                                val methods = endpoint.optJSONArray("methods") ?: continue
                                for (methodIndex in 0 until methods.length()) {
                                    val method = methods.optString(methodIndex).uppercase()
                                    if (path.isNotBlank()) routes.add("$method $path")
                                }
                            }
                        }
                        _uiState.update { it.copy(bridgeRoutes = routes) }
                        if (names.isNotEmpty()) {
                            _uiState.update { state ->
                                val selected = state.selectedModel.takeIf(names::contains) ?: names.first()
                                state.copy(availableModels = names, selectedModel = selected, visionCapableModels = vision, isConnected = true)
                            }
                            preferencesRepository.saveSelectedModel(_uiState.value.selectedModel)
                        }
                    }
                }
                .onFailure { _uiState.update { it.copy(bridgeHelp = null, bridgeRoutes = null) } }
        }
    }

    fun openAgentNotes() {
        _uiState.update { it.copy(showAgentNotesDialog = true, notesError = null) }
        syncAgentNotes()
    }

    fun closeAgentNotes() { _uiState.update { it.copy(showAgentNotesDialog = false) } }

    fun updateNotesDraft(value: String) { _uiState.update { it.copy(notesDraft = value.take(10_000)) } }

    fun syncAgentNotes() {
        viewModelScope.launch {
            _uiState.update { it.copy(notesLoading = true, notesError = null) }
            runCatching {
                val afterId = preferencesRepository.getNotesAfterId()
                val response = chatRepository.getAgentNotes(_uiState.value.agentBaseUrl, afterId)
                response to afterId
            }.onSuccess { (response, _) ->
                _uiState.update { state ->
                    val merged = (state.agentNotes + response.notes).distinctBy { it.id }.takeLast(200)
                    state.copy(agentNotes = merged, notesLoading = false)
                }
                preferencesRepository.saveNotesAfterId(response.next_after_id)
            }.onFailure { error ->
                _uiState.update { it.copy(notesLoading = false, notesError = error.displayMessage("Could not sync notes.")) }
            }
        }
    }

    fun postAgentNote() {
        val note = _uiState.value.notesDraft.trim()
        if (note.isBlank() || _uiState.value.notesPosting) return
        viewModelScope.launch {
            _uiState.update { it.copy(notesPosting = true, notesError = null) }
            runCatching {
                chatRepository.postAgentNote(
                    _uiState.value.agentBaseUrl,
                    AgentNoteRequest(source = "Android client", category = "implementation", note = note)
                )
            }.onSuccess { raw ->
                val id = runCatching { JSONObject(raw).optLong("note_id") }.getOrDefault(0L)
                _uiState.update { state ->
                    state.copy(
                        notesPosting = false,
                        notesDraft = "",
                        agentNotes = state.agentNotes + AgentNoteDto(id = id, source = "Android client", category = "implementation", note = note)
                    )
                }
                if (id > 0) preferencesRepository.saveNotesAfterId(id)
            }.onFailure { error ->
                _uiState.update { it.copy(notesPosting = false, notesError = error.displayMessage("Could not post note.")) }
            }
        }
    }

    fun uploadFile(uri: Uri, context: Context, destination: String? = null) {
        lastUploadUri = uri
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, uploadProgress = 0f, canRetryUpload = false, errorMessage = null) }
            try {
                var fileName = "attached_file"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        fileName = cursor.getString(nameIndex)
                    }
                }

                val mimeType = context.contentResolver.getType(uri) ?: ""
                val fileBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw Exception("Cannot read file content")

                val baseUrl = _uiState.value.activeBaseUrl
                val target = destination?.takeIf(String::isNotBlank) ?: "workspace"
                val result = chatRepository.uploadFile(baseUrl, fileName, fileBytes, mimeType, destination = target) { sent, total ->
                    _uiState.update { it.copy(uploadProgress = if (total > 0) sent.toFloat() / total else null) }
                }

                result.onSuccess {
                    val userMsg = ChatMessage(role = "user", content = "Uploaded file '$fileName' to workspace.")
                    _uiState.update { state ->
                        state.copy(
                            messages = state.messages + userMsg,
                            isUploading = false,
                            uploadProgress = null,
                            canRetryUpload = false
                        )
                    }
                }.onFailure { error ->
                    val tooLarge = error.localizedMessage?.contains("HTTP 413") == true
                    _uiState.update { state ->
                        state.copy(
                            isUploading = false,
                            uploadProgress = null,
                            canRetryUpload = !tooLarge,
                            errorMessage = if (tooLarge) "Upload rejected. ${error.localizedMessage} Choose a smaller file." else "Upload failed: ${error.localizedMessage}"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(
                        isUploading = false,
                        uploadProgress = null,
                        canRetryUpload = true,
                        errorMessage = "Error reading file: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun retryLastUpload() { lastUploadUri?.let { uploadFile(it, getApplication()) } }

    fun addChatImage(uri: Uri, context: Context) {
        viewModelScope.launch {
            runCatching {
                val current = _uiState.value.pendingChatImages
                require(current.size < 4) { "You can attach up to 4 images to one prompt." }
                val mimeType = context.contentResolver.getType(uri).orEmpty()
                require(mimeType.startsWith("image/")) { "Please choose an image file." }
                val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8 * 1024)
                    var total = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= 12 * 1024 * 1024) { "This image is too large for the server's 16 MB encoded limit." }
                        output.write(buffer, 0, read)
                    }
                    output.toByteArray()
                } ?: error("Could not read the selected image.")
                val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
                require(current.sumOf { it.length.toLong() } + encoded.length <= 16L * 1024 * 1024) {
                    "Attached images exceed the server's 16 MB encoded limit. Choose smaller images."
                }
                _uiState.update { it.copy(pendingChatImages = it.pendingChatImages + encoded, errorMessage = null) }
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.localizedMessage ?: "Could not attach this image.") }
            }
        }
    }

    fun clearChatImages() {
        _uiState.update { it.copy(pendingChatImages = emptyList()) }
    }

    fun openSystemStats() {
        _uiState.update { it.copy(showStatsDialog = true) }
        fetchSystemStats()
        fetchServerMetrics()
        start15sStatsPolling()
    }

    fun runDirectCommand(command: String, cwd: String = "", timeoutSeconds: Int = 120) {
        val trimmedCommand = command.trim()
        if (trimmedCommand.isBlank() || _uiState.value.isStreaming) return
        val safeTimeout = timeoutSeconds.coerceIn(1, 600)
        val sessionId = _uiState.value.sessionId
        val user = ChatMessage(role = "user", content = "Run directly: $trimmedCommand")
        val assistantId = UUID.randomUUID().toString()
        val placeholder = ChatMessage(id = assistantId, role = "assistant", content = "", isStreaming = true)
        val requestGeneration = ++activeRequestGeneration
        _uiState.update { it.copy(messages = it.messages + user + placeholder, isStreaming = true, inputText = "") }
        streamingJob = viewModelScope.launch {
            try {
                ensureSessionForMessage(sessionId, user.content)
                chatRepository.saveMessage(sessionId, user)
                chatRepository.saveMessage(sessionId, placeholder)
                val response = chatRepository.runCommand(
                    _uiState.value.agentBaseUrl,
                    CommandRequest(trimmedCommand, cwd, safeTimeout)
                )
                val result = response.result
                val args = JsonObject().apply {
                    addProperty("command", trimmedCommand)
                    addProperty("cwd", cwd)
                }
                val resultTool = ToolResultDto(
                    tool = "command",
                    arguments = args,
                    output = if (result?.stdout.isNullOrBlank() && result?.stderr.isNullOrBlank()) response.message?.content else null,
                    stdout = result?.stdout,
                    stderr = result?.stderr,
                    exit_code = result?.exit_code,
                    timed_out = result?.timed_out
                )
                val status = when {
                    result?.timed_out == true -> "Command timed out."
                    result?.exit_code != null -> "Command finished with exit code ${result.exit_code}."
                    else -> response.message?.content?.takeIf { it.isNotBlank() } ?: "Command completed."
                }
                val duration = response.duration_ms?.let { " (${it} ms)" }.orEmpty()
                replaceAssistant(assistantId) {
                    it.copy(
                        content = status + duration,
                        toolResults = listOf(resultTool),
                        isStreaming = false,
                        isJobFailure = result?.timed_out == true || (result?.exit_code != null && result.exit_code != 0),
                    )
                }
                recordAudit("Direct command: $trimmedCommand", listOf(status, result?.stdout, result?.stderr).filterNotNull().joinToString("\n"), succeeded = result?.exit_code == 0 || result?.exit_code == null)
                if (!AppVisibility.isForeground && _uiState.value.notifyReplies) {
                    val output = listOfNotNull(result?.stdout, result?.stderr).filter { it.isNotBlank() }.joinToString("\n")
                    AgentNotifications.notifyReply(getApplication(), sessionId, trimmedCommand, "$status$duration\n$output")
                }
            } catch (cancelled: CancellationException) {
                replaceAssistant(assistantId) {
                    it.copy(
                        content = "[Cancelled] The request was stopped. The command may already have executed; check the workstation before retrying.",
                        isStreaming = false
                    )
                }
            } catch (error: Exception) {
                val detail = (error as? HttpException)?.let(::httpErrorDetail)
                    ?: error.displayMessage("The request failed.")
                val errorText = "[Command Error] $detail The command may already have executed; check workstation state before retrying."
                replaceAssistant(assistantId) { it.copy(content = errorText, isStreaming = false, isJobFailure = true) }
                recordAudit("Direct command: $trimmedCommand", errorText, succeeded = false)
                if (!AppVisibility.isForeground && _uiState.value.notifyReplies) AgentNotifications.notifyReply(getApplication(), sessionId, trimmedCommand, errorText)
            } finally {
                if (requestGeneration == activeRequestGeneration) {
                    _uiState.update { it.copy(isStreaming = false) }
                    streamingJob = null
                }
                persistAssistant(sessionId, assistantId)
            }
        }
    }

    fun startBackgroundCommand(command: String, cwd: String = "", timeoutSeconds: Int = 600) {
        val trimmedCommand = command.trim()
        if (trimmedCommand.isBlank()) return
        if (_uiState.value.bridgeRoutes?.contains("POST /api/jobs") == false) {
            _uiState.update {
                it.copy(
                    showJobsDialog = true,
                    jobsLoading = false,
                    jobsError = "Background jobs are not advertised by this server. Refresh capabilities after the Agent Bridge update."
                )
            }
            return
        }
        if (_uiState.value.isStartingJob) return
        _uiState.update { it.copy(isStartingJob = true, jobsLoading = true, jobsError = null, retryableJobCommand = null, jobRetrySecondsRemaining = null) }
        viewModelScope.launch {
            try {
                val job = chatRepository.startJob(
                    _uiState.value.agentBaseUrl,
                    CommandRequest(trimmedCommand, cwd, timeoutSeconds.coerceIn(1, 3600))
                )
                JobCompletionMonitor.enqueue(getApplication(), _uiState.value.agentBaseUrl, job.job_id)
                recordAudit("Background command: $trimmedCommand", "Started job ${job.job_id} (${job.status}).", succeeded = true)
                _uiState.update { state -> state.copy(jobs = listOf(job) + state.jobs.filterNot { it.job_id == job.job_id }, jobsLoading = false, isStartingJob = false, showJobsDialog = true) }
                appendBridgeEvent("Started background job ${job.job_id}: ${job.command}")
                startJobsPolling()
            } catch (error: Exception) {
                val httpError = error as? HttpException
                val detail = httpError?.let(::httpErrorDetail) ?: error.displayMessage("Could not start background job.")
                if (httpError?.code() == 429) {
                    val waitSeconds = httpError.response()?.headers()?.get("Retry-After")?.toIntOrNull()?.coerceIn(1, 60) ?: 10
                    _uiState.update {
                        it.copy(
                            jobsLoading = false,
                            isStartingJob = false,
                            showJobsDialog = true,
                            jobsError = "The server job queue is full (HTTP 429): $detail",
                            retryableJobCommand = trimmedCommand,
                            jobRetryDelaySeconds = waitSeconds
                        )
                    }
                    startJobsPolling()
                } else {
                    _uiState.update { it.copy(jobsLoading = false, isStartingJob = false, showJobsDialog = true, jobsError = detail) }
                }
                recordAudit("Background command: $trimmedCommand", detail, succeeded = false)
            }
        }
    }

    private fun httpErrorDetail(error: HttpException): String = ApiError.from(error).message

    fun retryQueuedJobAfterDelay() {
        val command = _uiState.value.retryableJobCommand ?: return
        jobRetryJob?.cancel()
        jobRetryJob = viewModelScope.launch {
            for (remaining in _uiState.value.jobRetryDelaySeconds downTo 1) {
                _uiState.update { it.copy(jobRetrySecondsRemaining = remaining) }
                delay(1_000)
            }
            _uiState.update { it.copy(jobRetrySecondsRemaining = null, retryableJobCommand = null) }
            startBackgroundCommand(command)
        }
    }

    fun cancelQueuedJobRetry() {
        jobRetryJob?.cancel()
        jobRetryJob = null
        _uiState.update { it.copy(retryableJobCommand = null, jobRetrySecondsRemaining = null) }
    }

    private suspend fun restoreActiveChatJob() {
        val job = preferencesRepository.getActiveChatJob() ?: return
        if (job.requestId.isBlank()) {
            preferencesRepository.clearActiveChatJob()
            return
        }
        val messages = chatRepository.getMessagesFlow(job.sessionId).first()
        if (messages.none { it.id == job.assistantMessageId }) {
            preferencesRepository.clearActiveChatJob()
            return
        }
        val generation = ++activeRequestGeneration
        activeAgentChatJobId = job.requestId
        _uiState.update { state ->
            state.copy(
                sessionId = job.sessionId,
                isStreaming = true,
                messages = state.messages.map { msg ->
                    if (msg.id == job.assistantMessageId) msg.copy(isStreaming = true, jobStatus = "running") else msg
                }
            )
        }
        val restoredRequest = com.example.mylocalai.model.ChatRequest(
            session_id = job.sessionId,
            model = _uiState.value.selectedModel,
            prompt = job.prompt,
            tool_mode = if (_uiState.value.delegateLongWork) "full" else "direct",
        )
        streamingJob = viewModelScope.launch {
            runAgentChatJobFlow(
                baseUrl = job.baseUrl,
                chatRequest = restoredRequest,
                pendingRequest = PendingAgentChatRequest(job.idempotencyKey, job.baseUrl, restoredRequest),
                assistantMsgId = job.assistantMessageId,
                messageSessionId = job.sessionId,
                userMsg = ChatMessage(role = "user", content = job.prompt),
                requestGeneration = generation,
                resume = job,
            )
        }
    }

    fun cancelAgentChatJobById(requestId: String, baseUrl: String? = null) {
        viewModelScope.launch {
            val url = baseUrl ?: _uiState.value.agentBaseUrl
            runCatching { chatRepository.cancelAgentChatJob(url, requestId) }
            if (activeAgentChatJobId == requestId) {
                activeAgentChatJobId = null
                preferencesRepository.clearActiveChatJob()
            }
        }
    }

    private suspend fun restoreSoftwareTasks() {
        _uiState.update { it.copy(softwareTasksRestoring = true) }
        try {
            val pending = preferencesRepository.getPendingSoftwareTaskCreate()
            val tracked = preferencesRepository.getTrackedSoftwareTasks()
            _uiState.update {
                it.copy(
                    pendingSoftwareTaskCreate = pending,
                    softwareTasks = if (it.softwareTasks.isEmpty()) tracked.map { t -> SoftwareTaskSummary(taskId = t.taskId, status = "cached", statusUrl = t.statusUrl, eventsUrl = t.eventsUrl, diffUrl = t.diffUrl) } else it.softwareTasks,
                    softwareTaskError = if (pending != null) "A task create request has an unknown outcome. Recover it using the saved idempotency key; do not create another task." else it.softwareTaskError
                )
            }
            tracked.forEach { taskEventCursors[it.taskId] = it.eventCursor }
            tracked.forEach { item ->
                val task = refreshSoftwareTask(item.taskId, item.baseUrl)
                if (task == null || task.status.lowercase() in setOf("queued", "running")) {
                    SoftwareTaskMonitor.enqueue(getApplication(), item.baseUrl, item.taskId, item.statusUrl)
                }
            }
            startSoftwareTaskPollingIfNeeded()
        } catch (error: Exception) {
            _uiState.update { it.copy(softwareTaskError = "Could not restore saved task state: ${error.displayMessage("local storage error")}") }
        } finally {
            _uiState.update { it.copy(softwareTasksRestoring = false) }
        }
    }

    fun openSoftwareTasks() {
        _uiState.update { it.copy(showSoftwareTasksDialog = true, showBridgeToolsDialog = false, softwareTaskError = null) }
        if (!_uiState.value.softwareTasksRestoring) viewModelScope.launch { restoreSoftwareTasks() }
    }

    fun closeSoftwareTasks() {
        // Monitoring deliberately continues while this screen is closed.
        _uiState.update { it.copy(showSoftwareTasksDialog = false, softwareTaskDiff = null) }
    }

    fun createSoftwareTask(request: SoftwareTaskRequest) {
        if (_uiState.value.softwareTasksRestoring || _uiState.value.softwareTaskCreating || _uiState.value.pendingSoftwareTaskCreate != null) return
        if (_uiState.value.bridgeRoutes?.contains("POST /api/tasks") == false) {
            _uiState.update { it.copy(softwareTaskError = "This server does not advertise POST /api/tasks. Refresh server capabilities after updating Agent Bridge.") }
            return
        }
        val pending = PendingSoftwareTaskCreate(UUID.randomUUID().toString(), _uiState.value.agentBaseUrl, request)
        _uiState.update { it.copy(softwareTaskCreating = true, pendingSoftwareTaskCreate = pending, softwareTaskError = null) }
        viewModelScope.launch {
            try {
                preferencesRepository.savePendingSoftwareTaskCreate(pending)
                submitPendingSoftwareTask(pending, alreadyLocked = true)
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        softwareTaskCreating = false,
                        pendingSoftwareTaskCreate = null,
                        softwareTaskError = "Could not persist the idempotency key, so no task was submitted: ${error.displayMessage("storage error")}",
                    )
                }
            }
        }
    }

    fun retryPendingSoftwareTaskCreate() {
        val pending = _uiState.value.pendingSoftwareTaskCreate ?: return
        submitPendingSoftwareTask(pending)
    }

    private fun submitPendingSoftwareTask(pending: PendingSoftwareTaskCreate, alreadyLocked: Boolean = false) {
        if (!alreadyLocked && _uiState.value.softwareTaskCreating) return
        if (!alreadyLocked) _uiState.update { it.copy(softwareTaskCreating = true, softwareTaskError = null) }
        viewModelScope.launch {
            try {
                val raw = chatRepository.createSoftwareTask(pending.baseUrl, pending.idempotencyKey, pending.request)
                val taskId = SoftwareTaskParser.taskIdFromCreate(raw)
                if (taskId.isBlank()) {
                    _uiState.update {
                        it.copy(
                            softwareTaskCreating = false,
                            softwareTaskError = "The server response did not include a task ID. The create outcome is uncertain; retry only with the saved idempotency key."
                        )
                    }
                    return@launch
                }
                val initialTask = runCatching { SoftwareTaskParser.parseTask(raw, taskId) }.getOrNull()
                val tracks = preferencesRepository.getTrackedSoftwareTasks() + TrackedSoftwareTask(
                    taskId = taskId,
                    baseUrl = pending.baseUrl,
                    statusUrl = initialTask?.statusUrl,
                    eventsUrl = initialTask?.eventsUrl,
                    diffUrl = initialTask?.diffUrl,
                )
                preferencesRepository.saveTrackedSoftwareTasks(tracks)
                SoftwareTaskMonitor.enqueue(getApplication(), pending.baseUrl, taskId, initialTask?.statusUrl)
                preferencesRepository.clearPendingSoftwareTaskCreate()
                _uiState.update {
                    it.copy(
                        softwareTaskCreating = false,
                        pendingSoftwareTaskCreate = null,
                        selectedSoftwareTaskId = taskId,
                        softwareTaskError = null,
                        softwareTaskEvents = emptyList()
                    )
                }
                initialTask?.let(::upsertSoftwareTask)
                refreshSoftwareTask(taskId, pending.baseUrl)
                startSoftwareTaskPollingIfNeeded()
            } catch (error: Exception) {
                val http = error as? HttpException
                val detail = http?.let(::httpErrorDetail) ?: error.displayMessage("Task creation failed.")
                when {
                    http?.code() == 409 -> _uiState.update {
                        it.copy(softwareTaskCreating = false, softwareTaskError = "HTTP 409: $detail. Keep the saved key and request unchanged while resolving this conflict.")
                    }
                    http != null && http.code() in 400..499 -> {
                        preferencesRepository.clearPendingSoftwareTaskCreate()
                        val message = if (http.code() == 429) "Task capacity reached (HTTP 429): $detail. No automatic retry was made."
                        else "HTTP ${http.code()}: $detail"
                        _uiState.update { it.copy(softwareTaskCreating = false, pendingSoftwareTaskCreate = null, softwareTaskError = message) }
                    }
                    else -> _uiState.update {
                        it.copy(
                            softwareTaskCreating = false,
                            softwareTaskError = "Create outcome unknown: $detail. The request was not retried. Use Recover with the same idempotency key after checking connectivity."
                        )
                    }
                }
            }
        }
    }

    private fun upsertSoftwareTask(task: SoftwareTaskSummary) {
        _uiState.update { state ->
            state.copy(softwareTasks = (listOf(task) + state.softwareTasks.filterNot { it.taskId == task.taskId }).take(100))
        }
    }

    private fun extractSoftwareTaskOffers(response: com.example.mylocalai.model.ChatResponse): List<SoftwareTaskOffer> {
        if (response.software_tasks.isNotEmpty()) return response.software_tasks
        return response.tool_results.filter { it.tool == "start_software_task" }.mapNotNull { toolResult ->
            val data = runCatching { toolResult.data?.asJsonObject }.getOrNull() ?: return@mapNotNull null
            val task = data.getAsJsonObject("task") ?: data
            val taskId = task.get("task_id")?.asString?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            SoftwareTaskOffer(
                task_id = taskId,
                status = task.get("status")?.asString ?: "queued",
                phase = task.get("phase")?.asString ?: "queued",
                reused = data.get("reused")?.asBoolean ?: false,
                status_url = task.get("status_url")?.asString ?: "/api/tasks/$taskId",
                events_url = task.get("events_url")?.asString ?: "/api/tasks/$taskId/events",
                diff_url = task.get("diff_url")?.asString ?: "/api/tasks/$taskId/diff",
            )
        }
    }

    private suspend fun registerSoftwareTaskOffers(offers: List<SoftwareTaskOffer>, baseUrl: String) {
        val validOffers = offers.filter { it.task_id.isNotBlank() }
        if (validOffers.isEmpty()) return
        val existing = preferencesRepository.getTrackedSoftwareTasks().associateBy { it.taskId }
        val offeredTracks = validOffers.map { offer ->
            val old = existing[offer.task_id]
            TrackedSoftwareTask(
                taskId = offer.task_id,
                baseUrl = baseUrl,
                eventCursor = old?.eventCursor ?: 0L,
                statusUrl = offer.status_url ?: old?.statusUrl,
                eventsUrl = offer.events_url ?: old?.eventsUrl,
                diffUrl = offer.diff_url ?: old?.diffUrl,
            )
        }
        val offeredIds = validOffers.mapTo(mutableSetOf()) { it.task_id }
        preferencesRepository.saveTrackedSoftwareTasks(existing.values.filterNot { it.taskId in offeredIds } + offeredTracks)
        val summaries = validOffers.map { offer ->
            SoftwareTaskSummary(
                taskId = offer.task_id,
                status = offer.status,
                phase = offer.phase,
                statusUrl = offer.status_url,
                eventsUrl = offer.events_url,
                diffUrl = offer.diff_url,
                reused = offer.reused,
            )
        }
        summaries.forEach(::upsertSoftwareTask)
        // Track and poll quietly in the background; the conversation owns the UX.
        // Never auto-open the task screen from a chat message.
        _uiState.update {
            it.copy(
                selectedSoftwareTaskId = summaries.first().taskId,
                softwareTaskEvents = emptyList(),
                softwareTaskError = null,
            )
        }
        offeredTracks.forEach { track ->
            val status = summaries.firstOrNull { it.taskId == track.taskId }?.status?.lowercase()
            if (status in setOf("queued", "running")) {
                SoftwareTaskMonitor.enqueue(getApplication(), track.baseUrl, track.taskId, track.statusUrl)
            }
            refreshSoftwareTask(track.taskId, track.baseUrl)
        }
        startSoftwareTaskPollingIfNeeded()
    }

    private suspend fun refreshSoftwareTask(taskId: String, baseUrl: String? = null): SoftwareTaskSummary? {
        val tracked = preferencesRepository.getTrackedSoftwareTasks().firstOrNull { it.taskId == taskId }
        val url = baseUrl ?: tracked?.baseUrl ?: _uiState.value.agentBaseUrl
        return try {
            val raw = chatRepository.getSoftwareTask(url, taskId, tracked?.statusUrl)
            val task = SoftwareTaskParser.parseTask(raw, taskId)
            upsertSoftwareTask(task)
            try {
                val cursor = taskEventCursors[taskId] ?: tracked?.eventCursor ?: 0L
                val page = SoftwareTaskParser.parseEvents(chatRepository.getSoftwareTaskEvents(url, taskId, cursor, tracked?.eventsUrl), cursor)
                if (page.events.isNotEmpty()) {
                    val savedEvents = preferencesRepository.getSoftwareTaskEvents(taskId)
                    val mergedEvents = (savedEvents + page.events).distinctBy { it.id }.takeLast(300)
                    preferencesRepository.saveSoftwareTaskEvents(taskId, mergedEvents)
                    if (_uiState.value.selectedSoftwareTaskId == taskId) {
                        _uiState.update { it.copy(softwareTaskEvents = mergedEvents) }
                    }
                }
                if (page.nextAfterId > cursor) {
                    taskEventCursors[taskId] = page.nextAfterId
                    val latest = preferencesRepository.getTrackedSoftwareTasks().map {
                        if (it.taskId == taskId) it.copy(eventCursor = page.nextAfterId) else it
                    }
                    preferencesRepository.saveTrackedSoftwareTasks(latest)
                }
            } catch (eventError: Exception) {
                val detail = (eventError as? HttpException)?.let(::httpErrorDetail)
                    ?: eventError.displayMessage("Task events could not be refreshed.")
                _uiState.update { it.copy(softwareTaskError = "Task $taskId event history: $detail") }
            }
            task
        } catch (error: Exception) {
            val detail = (error as? HttpException)?.let(::httpErrorDetail) ?: error.displayMessage("Task refresh failed.")
            _uiState.update { it.copy(softwareTaskError = "Task $taskId: $detail") }
            null
        }
    }

    private suspend fun softwareTaskBaseUrl(taskId: String): String =
        preferencesRepository.getTrackedSoftwareTasks().firstOrNull { it.taskId == taskId }?.baseUrl
            ?: _uiState.value.agentBaseUrl

    private fun startSoftwareTaskPollingIfNeeded() {
        if (softwareTaskPollingJob?.isActive == true) return
        softwareTaskPollingJob = viewModelScope.launch {
            var waitMs = 1_500L
            while (isActive) {
                val tracked = preferencesRepository.getTrackedSoftwareTasks()
                var active = false
                for (item in tracked) {
                    val before = _uiState.value.softwareTasks.firstOrNull { it.taskId == item.taskId }
                    if (before?.status?.lowercase() in setOf("queued", "running", "paused", "needs_input") || before == null) {
                        val current = refreshSoftwareTask(item.taskId, item.baseUrl)
                        if (current?.status?.lowercase() in setOf("queued", "running")) active = true
                        current?.let(::notifySoftwareTaskProgressIfNeeded)
                    }
                }
                if (!active) break
                delay(waitMs)
                waitMs = (waitMs * 2).coerceAtMost(15_000L)
            }
        }
    }

    private fun notifySoftwareTaskProgressIfNeeded(task: SoftwareTaskSummary) {
        if (AppVisibility.isForeground || !_uiState.value.notifyTasks) return
        val signature = "${task.status}|${task.phase}|${task.stepsDone}|${task.stepsTotal}"
        if (lastTaskNotifications.put(task.taskId, signature) != signature) {
            AgentNotifications.notifySoftwareTaskProgress(getApplication(), task.taskId, task.objective, task.status, task.phase, task.stepsDone, task.stepsTotal)
        }
    }

    fun selectSoftwareTask(taskId: String) {
        _uiState.update { it.copy(selectedSoftwareTaskId = taskId, softwareTaskEvents = emptyList(), softwareTaskLoading = true, softwareTaskError = null, softwareTaskDiff = null) }
        viewModelScope.launch {
            _uiState.update { it.copy(softwareTaskEvents = preferencesRepository.getSoftwareTaskEvents(taskId)) }
            refreshSoftwareTask(taskId)
            _uiState.update { it.copy(softwareTaskLoading = false) }
        }
    }

    fun refreshSoftwareTaskNow(taskId: String) {
        _uiState.update { it.copy(softwareTaskLoading = true, softwareTaskError = null) }
        viewModelScope.launch {
            refreshSoftwareTask(taskId)
            _uiState.update { it.copy(softwareTaskLoading = false) }
        }
    }

    fun resumeSoftwareTask(taskId: String, message: String, additionalSteps: Int = 0, additionalSeconds: Int = 0) {
        val cleanMessage = message.trim()
        if (cleanMessage.isBlank()) {
            _uiState.update { it.copy(softwareTaskError = "Review the task and enter a reconciliation message before resuming.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(softwareTaskLoading = true, softwareTaskError = null) }
            try {
                val baseUrl = softwareTaskBaseUrl(taskId)
                chatRepository.resumeSoftwareTask(
                    baseUrl,
                    taskId,
                    SoftwareTaskResumeRequest(
                        message = cleanMessage,
                        additional_steps = additionalSteps.takeIf { it > 0 },
                        additional_seconds = additionalSeconds.takeIf { it > 0 },
                    )
                )
                _uiState.update { it.copy(softwareTaskLoading = false) }
                refreshSoftwareTask(taskId)
                SoftwareTaskMonitor.enqueue(getApplication(), baseUrl, taskId)
                startSoftwareTaskPollingIfNeeded()
            } catch (error: Exception) {
                val detail = (error as? HttpException)?.let(::httpErrorDetail) ?: error.displayMessage("Could not resume task.")
                if (error !is HttpException) refreshSoftwareTask(taskId)
                _uiState.update {
                    it.copy(
                        softwareTaskLoading = false,
                        softwareTaskError = if (error !is HttpException) {
                            "Resume outcome is uncertain: $detail. Status and events were refreshed; inspect the latest events and workspace before manually resuming again. No automatic retry was made."
                        } else "${error.code()}: $detail"
                    )
                }
            }
        }
    }

    fun cancelSoftwareTask(taskId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(softwareTaskLoading = true, softwareTaskError = null) }
            try {
                chatRepository.cancelSoftwareTask(softwareTaskBaseUrl(taskId), taskId)
                refreshSoftwareTask(taskId)
                _uiState.update { it.copy(softwareTaskLoading = false) }
            } catch (error: Exception) {
                val detail = (error as? HttpException)?.let(::httpErrorDetail) ?: error.displayMessage("Could not cancel task.")
                if (error !is HttpException) refreshSoftwareTask(taskId)
                _uiState.update {
                    it.copy(
                        softwareTaskLoading = false,
                        softwareTaskError = if (error !is HttpException) "Cancel outcome is uncertain: $detail. Task status was refreshed; no second cancellation was sent." else "${error.code()}: $detail"
                    )
                }
            }
        }
    }

    fun loadSoftwareTaskDiff(taskId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(softwareTaskDiffLoading = true, softwareTaskError = null) }
            try {
                val tracked = preferencesRepository.getTrackedSoftwareTasks().firstOrNull { it.taskId == taskId }
                val diff = SoftwareTaskParser.parseDiff(chatRepository.getSoftwareTaskDiff(softwareTaskBaseUrl(taskId), taskId, tracked?.diffUrl))
                _uiState.update { it.copy(softwareTaskDiffLoading = false, softwareTaskDiff = diff) }
            } catch (error: Exception) {
                val detail = (error as? HttpException)?.let(::httpErrorDetail) ?: error.displayMessage("Could not load task diff.")
                _uiState.update { it.copy(softwareTaskDiffLoading = false, softwareTaskError = detail) }
            }
        }
    }

    fun saveSoftwareTaskDiff(uri: Uri, diff: SoftwareTaskDiff) {
        viewModelScope.launch {
            runCatching {
                val text = buildString {
                    appendLine("Base commit: ${diff.baseCommit}")
                    appendLine("Worktree path: ${diff.worktreePath}")
                    appendLine("Truncated: ${diff.truncated}")
                    appendLine("Untracked files: ${diff.untrackedFiles.joinToString()}")
                    if (diff.truncated) appendLine("WARNING: The server truncated this diff; some changes are omitted.")
                    appendLine("\n--- DIFF ---\n")
                    append(diff.diff)
                }
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use {
                        it.write(text.toByteArray(Charsets.UTF_8))
                    } ?: error("Could not open the selected file for writing.")
                }
            }.onFailure { error -> _uiState.update { it.copy(softwareTaskError = error.displayMessage("Could not export the task diff.")) } }
        }
    }

    fun closeSoftwareTaskDiff() { _uiState.update { it.copy(softwareTaskDiff = null, softwareTaskDeleteConfirmation = null) } }
    fun confirmDeleteSoftwareTaskWorktree(taskId: String) { _uiState.update { it.copy(softwareTaskDeleteConfirmation = taskId) } }
    fun dismissDeleteSoftwareTaskWorktree() { _uiState.update { it.copy(softwareTaskDeleteConfirmation = null) } }

    fun deleteSoftwareTaskWorktree(taskId: String) {
        viewModelScope.launch {
            try {
                chatRepository.deleteSoftwareTaskWorktree(softwareTaskBaseUrl(taskId), taskId)
                _uiState.update { state ->
                    state.copy(
                        softwareTaskDeleteConfirmation = null,
                        softwareTaskError = null,
                        softwareTaskDiff = state.softwareTaskDiff?.copy(worktreePath = "Removed")
                    )
                }
            } catch (error: Exception) {
                val detail = (error as? HttpException)?.let(::httpErrorDetail) ?: error.displayMessage("Could not remove task worktree.")
                val uncertain = error !is HttpException
                if (uncertain) {
                    refreshSoftwareTask(taskId)
                    val refreshedDiff = runCatching {
                        SoftwareTaskParser.parseDiff(chatRepository.getSoftwareTaskDiff(softwareTaskBaseUrl(taskId), taskId))
                    }.getOrNull()
                    _uiState.update { it.copy(softwareTaskDiff = refreshedDiff) }
                }
                _uiState.update {
                    it.copy(
                        softwareTaskDeleteConfirmation = null,
                        softwareTaskError = if (uncertain) "Worktree removal outcome is uncertain: $detail. Task status and diff were rechecked; review them before taking any further action. No automatic retry was made."
                        else detail
                    )
                }
            }
        }
    }

    fun openJobs() {
        _uiState.update { it.copy(showJobsDialog = true, showBridgeToolsDialog = false) }
        if (_uiState.value.bridgeRoutes?.contains("GET /api/jobs") == false) {
            _uiState.update { it.copy(jobsError = "This server manifest does not advertise background jobs. Refresh capabilities after the Agent Bridge server is updated.") }
            return
        }
        monitorActiveJobs()
        startJobsPolling()
    }

    fun closeJobs() {
        jobsPollingJob?.cancel()
        cancelQueuedJobRetry()
        _uiState.update { it.copy(showJobsDialog = false) }
    }

    private fun startJobsPolling() {
        jobsPollingJob?.cancel()
        jobsPollingJob = viewModelScope.launch {
            var pollDelayMs = 1_500L
            while (isActive && _uiState.value.showJobsDialog) {
                _uiState.update { it.copy(jobsLoading = it.jobs.isEmpty()) }
                val result = runCatching { chatRepository.getJobs(_uiState.value.agentBaseUrl) }
                result.onSuccess { jobs ->
                    _uiState.update { it.copy(jobs = jobs, jobsLoading = false) }
                    val hasActive = jobs.any { it.status.lowercase() in ACTIVE_JOB_STATUSES }
                    pollDelayMs = if (hasActive) (pollDelayMs * 2).coerceAtMost(10_000L) else 15_000L
                }.onFailure { error ->
                    _uiState.update { it.copy(jobsLoading = false, jobsError = error.displayMessage("Could not refresh jobs.")) }
                    pollDelayMs = (pollDelayMs * 2).coerceAtMost(30_000L)
                }
                val active = _uiState.value.jobs.any { it.status.lowercase() in ACTIVE_JOB_STATUSES }
                if (!active && result.isSuccess) pollDelayMs = 15_000L
                delay(pollDelayMs)
            }
        }
    }

    fun cancelBackgroundJob(jobId: String) {
        viewModelScope.launch {
            try {
                chatRepository.cancelJob(_uiState.value.agentBaseUrl, jobId)
                recordAudit("Cancel job: $jobId", "Cancellation requested.", succeeded = true)
                refreshJobsOnce()
            } catch (error: Exception) {
                val detail = error.displayMessage("Could not cancel this job.")
                _uiState.update { it.copy(jobsError = detail) }
                recordAudit("Cancel job: $jobId", detail, succeeded = false)
            }
        }
    }

    private suspend fun refreshJobsOnce() {
        try {
            chatRepository.getJobs(_uiState.value.agentBaseUrl).also { jobs ->
                _uiState.update { it.copy(jobs = jobs, jobsError = null) }
            }
        } catch (error: Exception) {
            _uiState.update { it.copy(jobsError = error.displayMessage("Could not refresh jobs.")) }
        }
    }

    fun openBridgeTools() { _uiState.update { it.copy(showBridgeToolsDialog = true, bridgeToolsResult = null) } }
    fun closeBridgeTools() { _uiState.update { it.copy(showBridgeToolsDialog = false) } }
    fun openAuditHistory() { _uiState.update { it.copy(showAuditHistoryDialog = true) } }
    fun closeAuditHistory() { _uiState.update { it.copy(showAuditHistoryDialog = false) } }
    fun clearAuditHistory() {
        viewModelScope.launch { runCatching { preferencesRepository.clearBridgeAuditHistory() } }
    }

    private fun runBridgeTool(auditAction: String, action: suspend () -> String) {
        if (_uiState.value.bridgeToolsBusy) return
        _uiState.update { it.copy(bridgeToolsBusy = true, bridgeToolsResult = null) }
        viewModelScope.launch {
            try {
                val result = action()
                _uiState.update { it.copy(bridgeToolsBusy = false, bridgeToolsResult = result) }
                recordAudit(auditAction, result, succeeded = true)
                maybeTrackReturnedJob(result)
            } catch (cancelled: CancellationException) {
                _uiState.update { it.copy(bridgeToolsBusy = false) }
                throw cancelled
            } catch (error: Exception) {
                val detail = (error as? HttpException)?.let(::httpErrorDetail)
                    ?: error.displayMessage("Unknown error.")
                _uiState.update { it.copy(bridgeToolsBusy = false, bridgeToolsResult = "Request failed: $detail") }
                recordAudit(auditAction, detail, succeeded = false)
            }
        }
    }

    private fun recordAudit(action: String, detail: String, succeeded: Boolean) {
        viewModelScope.launch {
            runCatching { preferencesRepository.recordBridgeAudit(action, detail, succeeded) }
        }
    }

    fun searchWeb(query: String) {
        runBridgeTool("Web search: $query") {
            val response = chatRepository.searchWeb(_uiState.value.agentBaseUrl, query)
            _uiState.update { it.copy(searchResults = response) }
            response.error ?: response.results.joinToString("\n\n") { "${it.title}\n${it.url}\n${it.snippet}" }
        }
    }

    fun fetchProcesses() = runBridgeTool("List top CPU processes") { chatRepository.getProcesses(_uiState.value.agentBaseUrl) }
    fun operateService(name: String, action: String) = runBridgeTool("Service $action: $name") {
        chatRepository.serviceAction(_uiState.value.agentBaseUrl, ServiceRequest(name, action))
    }
    fun operatePackage(action: String, packageName: String = "") = runBridgeTool("Package action: $action ${packageName.trim()}".trim()) {
        chatRepository.packageAction(_uiState.value.agentBaseUrl, PackageRequest(action, packageName))
    }
    fun operateSchedule(request: ScheduleRequest) = runBridgeTool("Schedule action: ${request.action}") { chatRepository.scheduleAction(_uiState.value.agentBaseUrl, request) }
    fun queryApps(query: String = "") = runBridgeTool("Find desktop apps: $query") { chatRepository.getApps(_uiState.value.agentBaseUrl, query) }
    fun launchRemoteApp(applicationId: String, args: List<String> = emptyList()) = runBridgeTool("Launch desktop app: $applicationId") {
        chatRepository.launchApp(_uiState.value.agentBaseUrl, AppLaunchRequest(applicationId, args))
    }
    fun openFileOnPc(path: String) = runBridgeTool("Open on PC: $path") {
        chatRepository.launchApp(_uiState.value.agentBaseUrl, AppLaunchRequest(path))
    }
    fun desktopAction(request: DesktopRequest) = runBridgeTool("Desktop action: ${request.action}") {
        val isScreenshot = request.action.equals("screenshot", ignoreCase = true)
        val response = try {
            chatRepository.desktopAction(_uiState.value.agentBaseUrl, request)
        } catch (error: HttpException) {
            if (!isScreenshot) throw error
            val body = runCatching { error.response()?.errorBody()?.string().orEmpty() }.getOrDefault("")
            throw java.io.IOException(formatScreenshotFailure(body, "HTTP ${error.code()}"))
        }
        if (isScreenshot) {
            val payload = runCatching { JSONObject(response) }.getOrNull()
            val failed = payload?.optBoolean("success", true) == false ||
                payload?.optString("status")?.lowercase() in setOf("error", "failed") ||
                !payload?.optString("error").isNullOrBlank()
            if (failed) return@runBridgeTool formatScreenshotFailure(response)
            val path = payload?.optString("path")?.takeIf { !it.isNullOrBlank() }
                ?: payload?.optString("saved_path")?.takeIf { !it.isNullOrBlank() }
                ?: payload?.optString("file_path")?.takeIf { !it.isNullOrBlank() }
                ?: payload?.optString("download_url")?.takeIf { it.isNotBlank() }?.let { url ->
                    runCatching { android.net.Uri.parse(url).getQueryParameter("path") }.getOrNull()
                }
            if (!path.isNullOrBlank()) {
                _uiState.update { it.copy(showBridgeToolsDialog = false) }
                openFileViewer(path)
                "Screenshot saved to $path. Loading preview…"
            } else if (payload != null && (payload.has("attempts") || payload.has("diagnostics"))) {
                formatScreenshotFailure(response, "Screenshot request did not return a downloadable image.")
            } else response
        } else response
    }

    private fun formatScreenshotFailure(raw: String, fallback: String = "Screenshot capture failed."): String {
        val payload = runCatching { JSONObject(raw) }.getOrNull()
            ?: return listOf(fallback, raw.takeIf { it.isNotBlank() }).filterNotNull().joinToString("\n")
        return buildList {
            val detail = listOf("error", "detail", "message").firstNotNullOfOrNull { key ->
                payload.optString(key).takeIf { it.isNotBlank() && it != "null" }
            }
            add(detail?.let { "Screenshot failed: $it" } ?: fallback)
            listOf("attempts", "diagnostics").forEach { key ->
                if (payload.has(key) && !payload.isNull(key)) add("${key.replaceFirstChar(Char::uppercase)}: ${payload.opt(key)}")
            }
            if (size == 1) add("Server response: $payload")
        }.joinToString("\n")
    }
    fun fetchDesktopDiagnostics() = runBridgeTool("Desktop diagnostics") {
        chatRepository.desktopDiagnostics(_uiState.value.agentBaseUrl)
    }

    fun openHealthDiagnostics() {
        _uiState.update { it.copy(showHealthDialog = true, showBridgeToolsDialog = false, healthError = null) }
        fetchHealth()
        fetchSystemStats()
    }

    fun closeHealthDiagnostics() { _uiState.update { it.copy(showHealthDialog = false) } }

    fun fetchHealth() {
        if (_uiState.value.bridgeRoutes?.contains("GET /api/health") == false) {
            _uiState.update {
                it.copy(
                    healthLoading = false,
                    healthResult = null,
                    healthError = "GET /api/health is not advertised by the current server manifest. Restart/update Agent Bridge, then refresh capabilities."
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(healthLoading = true, healthError = null) }
            runCatching { chatRepository.getHealth(_uiState.value.agentBaseUrl) }
                .onSuccess { result -> _uiState.update { it.copy(healthLoading = false, healthResult = result) } }
                .onFailure { error ->
                    val detail = (error as? HttpException)?.let(::httpErrorDetail) ?: error.displayMessage("Health request failed.")
                    _uiState.update { it.copy(healthLoading = false, healthResult = null, healthError = detail) }
                }
        }
    }

    fun fetchServerMetrics() {
        viewModelScope.launch {
            _uiState.update { it.copy(serverMetricsLoading = true, serverMetricsError = null) }
            runCatching { chatRepository.getHealth(_uiState.value.agentBaseUrl) }
                .onSuccess { raw ->
                    _uiState.update {
                        it.copy(
                            serverHealthMetrics = parseServerHealthMetrics(raw),
                            serverMetricsLoading = false,
                            serverMetricsError = null,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(serverMetricsLoading = false, serverMetricsError = error.displayMessage("Could not fetch server health.")) }
                }
        }
    }

    private fun parseServerHealthMetrics(raw: String): ServerHealthMetrics? {
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val memory = json.optJSONObject("memory")
        val availableMemoryBytes = memory?.optLong("available_bytes")?.takeIf { it > 0 }
            ?: memory?.optLong("available")?.takeIf { it > 0 }
        val system = json.optJSONObject("system")
        val diskFreeBytes = system?.optLong("disk_root_free_bytes")?.takeIf { it > 0 }
            ?: system?.optLong("disk_home_free_bytes")?.takeIf { it > 0 }
            ?: json.optLong("disk_free_bytes")?.takeIf { it > 0 }
        val diskTotalBytes = system?.optLong("disk_root_total_bytes")?.takeIf { it > 0 }
            ?: system?.optLong("disk_total_bytes")?.takeIf { it > 0 }
            ?: json.optLong("disk_total_bytes")?.takeIf { it > 0 }
        val diskFreePercent = when {
            diskFreeBytes != null && diskTotalBytes != null && diskTotalBytes > 0 ->
                ((diskFreeBytes.toFloat() / diskTotalBytes) * 100f).roundToInt().toFloat()
            else -> null
        }
        val chatJobs = json.optJSONObject("chat_jobs")
        val chatJobsRunning = chatJobs?.optInt("running")?.takeIf { it > 0 }
        val chatJobsPending = chatJobs?.let { obj ->
            listOf("running", "queued", "pending", "active", "delegated").sumOf { key -> obj.optInt(key).coerceAtLeast(0) }.takeIf { it > 0 }
        }
        val staleTaskHeartbeats = json.optJSONObject("workers")?.optInt("stale_task_heartbeats")?.takeIf { it > 0 }
        return ServerHealthMetrics(
            availableMemoryBytes = availableMemoryBytes,
            diskFreeBytes = diskFreeBytes,
            diskFreePercent = diskFreePercent,
            chatJobsPending = chatJobsPending,
            staleTaskHeartbeats = staleTaskHeartbeats,
            chatJobsRunning = chatJobsRunning,
        )
    }

    private fun rememberAgentRequestId(requestId: String): Boolean {
        val cleanId = requestId.trim()
        if (cleanId.isBlank()) return true
        val alreadyRendered = _uiState.value.messages.any { message ->
            message.actionSummary.lineSequence().any { it.trim() == "Response rendered: $cleanId" }
        }
        if (alreadyRendered || cleanId in handledAgentRequestIds) return false
        handledAgentRequestIds.add(cleanId)
        while (handledAgentRequestIds.size > 256) {
            handledAgentRequestIds.remove(handledAgentRequestIds.first())
        }
        return true
    }

    private fun agentChatRequestFingerprint(request: com.example.mylocalai.model.ChatRequest): String {
        val digest = MessageDigest.getInstance("SHA-256")
        listOf(request.session_id, request.model, request.tool_mode, request.prompt).forEach { value ->
            digest.update(value.toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
        }
        request.images.forEach { image ->
            digest.update(image.toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    fun fetchBridgeLogs() = runBridgeTool("Read server logs") { chatRepository.getLogs(_uiState.value.agentBaseUrl) }
    fun fetchModelTags() = runBridgeTool("Read installed model tags") { chatRepository.getTags(_uiState.value.agentBaseUrl) }

    private fun maybeTrackReturnedJob(raw: String) {
        val parsed = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val jobJson = parsed.optJSONObject("job") ?: parsed.takeIf { it.has("job_id") } ?: return
        val id = jobJson.optString("job_id").takeIf { it.isNotBlank() } ?: return
        JobCompletionMonitor.enqueue(getApplication(), _uiState.value.agentBaseUrl, id)
        // Track quietly; the user opens the jobs screen explicitly if they want it.
        startJobsPolling()
    }

    private fun monitorActiveJobs() {
        viewModelScope.launch {
            runCatching { chatRepository.getJobs(_uiState.value.agentBaseUrl) }
                .onSuccess { jobs ->
                    jobs.filter { it.status.lowercase() in ACTIVE_JOB_STATUSES }.forEach { job ->
                        JobCompletionMonitor.enqueue(getApplication(), _uiState.value.agentBaseUrl, job.job_id)
                    }
                }
        }
    }

    private fun appendBridgeEvent(text: String) {
        viewModelScope.launch {
            val sessionId = _uiState.value.sessionId
            val event = ChatMessage(role = "assistant", content = text)
            runCatching { chatRepository.saveMessage(sessionId, event) }
        }
    }

    private suspend fun ensureSessionForMessage(sessionId: String, prompt: String) {
        val session = chatRepository.getSession(sessionId)
        if (session == null) chatRepository.createSession(sessionId, sessionTitleFromPrompt(prompt))
        else if (session.title == "New chat" && chatRepository.getMessagesFlow(sessionId).first().isEmpty()) {
            chatRepository.renameSession(sessionId, sessionTitleFromPrompt(prompt))
        }
    }

    private fun replaceAssistant(id: String, transform: (ChatMessage) -> ChatMessage) {
        _uiState.update { state -> state.copy(messages = state.messages.map { if (it.id == id) transform(it) else it }) }
    }

    private suspend fun persistAssistant(sessionId: String, assistantId: String) {
        withContext(NonCancellable) {
            _uiState.value.messages.firstOrNull { it.id == assistantId }?.let { message ->
                runCatching { chatRepository.saveMessage(sessionId, message.copy(isStreaming = false)) }
            }
        }
    }

    fun fetchSystemStats() {
        viewModelScope.launch {
            _uiState.update { it.copy(isFetchingStats = true) }
            val baseUrl = _uiState.value.activeBaseUrl
            val result = chatRepository.fetchSystemStats(baseUrl)

            result.onSuccess { stats ->
                _uiState.update { it.copy(systemStats = stats, isFetchingStats = false) }
            }.onFailure { error ->
                _uiState.update { it.copy(isFetchingStats = false, errorMessage = "Stats error: ${error.localizedMessage}") }
            }
        }
    }

    fun closeSystemStats() {
        statsPollingJob?.cancel()
        _uiState.update { it.copy(showStatsDialog = false) }
    }

    fun openServerLogs(lines: Int = _uiState.value.serverLogsLines) {
        _uiState.update { it.copy(showServerLogsDialog = true, serverLogsLines = lines) }
        fetchServerLogs(lines)
    }

    fun fetchServerLogs(lines: Int = _uiState.value.serverLogsLines) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isFetchingLogs = true,
                    serverLogsLines = lines,
                    serverLogs = null,
                    serverLogsError = null,
                    serverLogsSaveMessage = null,
                    serverLogsSaveError = null
                )
            }
            val baseUrl = _uiState.value.activeBaseUrl
            val result = chatRepository.fetchServerLogs(baseUrl, lines)

            result.onSuccess { logs ->
                _uiState.update { it.copy(serverLogs = logs, isFetchingLogs = false, serverLogsError = null) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isFetchingLogs = false,
                        serverLogsError = error.displayMessage("Could not fetch server logs.")
                    )
                }
            }
        }
    }

    fun saveServerLogs(uri: Uri, logs: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(serverLogsSaveMessage = null, serverLogsSaveError = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { output ->
                        output.write(logs.toByteArray(Charsets.UTF_8))
                    } ?: error("Could not open the selected file for writing.")
                }
            }.onSuccess {
                _uiState.update { it.copy(serverLogsSaveMessage = "Server logs saved to the selected location.") }
            }.onFailure { error ->
                _uiState.update { it.copy(serverLogsSaveError = error.displayMessage("Could not save server logs.")) }
            }
        }
    }

    fun closeServerLogs() {
        _uiState.update { it.copy(showServerLogsDialog = false) }
    }

    fun clearFileDownloadStatus() {
        _uiState.update { it.copy(fileDownloadMessage = null, fileDownloadError = null) }
    }

    fun downloadRemoteFileToPhone(filePath: String, targetUri: Uri? = null, asRoot: Boolean = false) {
        val fileName = filePath.substringAfterLast('/').ifBlank { "remote_file" }
        _uiState.update {
            it.copy(
                isDownloadingRemoteFile = true,
                downloadingFileName = fileName,
                fileDownloadMessage = null,
                fileDownloadError = null
            )
        }

        viewModelScope.launch {
            val baseUrl = _uiState.value.agentBaseUrl
            val result = chatRepository.downloadFileBytes(baseUrl, filePath, asRoot = asRoot)

            result.onSuccess { bytes ->
                runCatching {
                    withContext(Dispatchers.IO) {
                        val context = getApplication<Application>()
                        if (targetUri != null) {
                            context.contentResolver.openOutputStream(targetUri)?.use { out ->
                                out.write(bytes)
                            } ?: error("Could not write to destination URI.")
                        } else {
                            saveBytesToDownloads(context, fileName, bytes)
                        }
                    }
                }.onSuccess {
                    _uiState.update {
                        it.copy(
                            isDownloadingRemoteFile = false,
                            downloadingFileName = null,
                            fileDownloadMessage = "Saved '$fileName' to Downloads."
                        )
                    }
                    if (_uiState.value.notifyDownloads) AgentNotifications.notifyDownload(
                        getApplication(), fileName, success = true,
                        detail = "Saved to Downloads",
                    )
                }.onFailure { saveError ->
                    _uiState.update {
                        it.copy(
                            isDownloadingRemoteFile = false,
                            downloadingFileName = null,
                            fileDownloadError = "Failed to save '$fileName': ${saveError.localizedMessage}"
                        )
                    }
                    if (_uiState.value.notifyDownloads) AgentNotifications.notifyDownload(
                        getApplication(), fileName, success = false,
                        detail = saveError.localizedMessage ?: "Could not save file",
                    )
                }
            }.onFailure { networkError ->
                _uiState.update {
                    it.copy(
                        isDownloadingRemoteFile = false,
                        downloadingFileName = null,
                        fileDownloadError = "Download error: ${networkError.localizedMessage}"
                    )
                }
                if (_uiState.value.notifyDownloads) AgentNotifications.notifyDownload(
                    getApplication(), fileName, success = false,
                    detail = networkError.localizedMessage ?: "Download failed",
                )
            }
        }
    }

    private fun saveBytesToDownloads(context: Context, fileName: String, bytes: ByteArray): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: error("Could not create entry in Downloads")
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(bytes)
                } ?: error("Could not open Downloads output stream")
                contentValues.clear()
                contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)
                return uri.toString()
            } catch (e: Exception) {
                context.contentResolver.delete(uri, null, null)
                throw e
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val file = File(downloadsDir, fileName)
            file.outputStream().use { it.write(bytes) }
            return file.absolutePath
        }
    }

    fun openFileViewer(filePath: String) {
        lastDownloadPath = filePath
        _uiState.update {
            it.copy(filePreviewState = FilePreviewState(filePath = filePath, isLoading = true))
        }
        viewModelScope.launch {
            val baseUrl = _uiState.value.agentBaseUrl
            val result = chatRepository.downloadFile(baseUrl, filePath) { received, total ->
                _uiState.update { state -> state.copy(filePreviewState = state.filePreviewState?.copy(downloadProgress = if (total > 0) received.toFloat() / total else null)) }
            }

            result.onSuccess { downloaded ->
                _uiState.update { state ->
                    state.copy(
                        filePreviewState = state.filePreviewState?.copy(
                            fileContent = downloaded.content,
                            mimeType = downloaded.mimeType,
                            imageBytes = downloaded.imageBytes,
                            byteCount = downloaded.byteCount,
                            isLoading = false,
                            downloadProgress = null,
                            canRetry = false
                        )
                    )
                }
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(
                        filePreviewState = state.filePreviewState?.copy(
                            isLoading = false,
                            downloadProgress = null,
                            canRetry = error !is IllegalArgumentException,
                            errorMessage = "Cannot download file: ${error.localizedMessage}"
                        )
                    )
                }
            }
        }
    }

    fun openRemoteFiles() {
        _uiState.update { it.copy(showRemoteFilesDialog = true, remoteFilesError = null) }
        refreshRemoteFiles()
    }

    /** Normalize a remote path: expand ~, collapse duplicate slashes, block traversal segments. */
    private fun normalizeRemotePath(path: String): String {
        val trimmed = path.trim()
        if (trimmed.isBlank() || trimmed == "~") return "~"
        val homeRelative = trimmed.startsWith("~/")
        val raw = if (homeRelative) trimmed.removePrefix("~/") else trimmed.removePrefix("/")
        val parts = raw.split('/').filter { it.isNotBlank() && it != "." && it != ".." }
        if (parts.isEmpty()) return if (homeRelative) "~" else "/"
        val joined = parts.joinToString("/")
        return if (homeRelative) "~/$joined" else "/$joined"
    }

    fun refreshRemoteFiles(path: String = _uiState.value.remoteFilesPath, asRoot: Boolean = _uiState.value.remoteFilesAsRoot) {
        val target = normalizeRemotePath(path)
        remoteFilesJob?.cancel()
        _uiState.update {
            it.copy(
                remoteFilesPath = target,
                remoteFilesAsRoot = asRoot,
                isLoadingRemoteFiles = true,
                remoteFilesError = null,
                remoteFilesTruncated = false,
                remoteFilesElevated = false
            )
        }
        remoteFilesJob = viewModelScope.launch {
            chatRepository.listRemoteFiles(_uiState.value.agentBaseUrl, target, asRoot)
                .onSuccess { listing ->
                    _uiState.update {
                        it.copy(
                            remoteFiles = listing.entries,
                            isLoadingRemoteFiles = false,
                            remoteFilesTruncated = listing.truncated,
                            remoteFilesElevated = listing.elevated,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            remoteFiles = emptyList(),
                            isLoadingRemoteFiles = false,
                            remoteFilesError = error.localizedMessage ?: "Could not load remote files."
                        )
                    }
                }
        }
    }

    fun closeRemoteFiles() {
        remoteFilesJob?.cancel()
        _uiState.update { it.copy(showRemoteFilesDialog = false, isLoadingRemoteFiles = false) }
    }

    fun navigateRemoteFilesUp() {
        val current = normalizeRemotePath(_uiState.value.remoteFilesPath)
        if (current == "/" || current == "~") return
        val parent = current.substringBeforeLast('/').ifBlank { "/" }
        refreshRemoteFiles(parent)
    }

    fun setRemoteFilesAsRoot(enabled: Boolean) {
        refreshRemoteFiles(_uiState.value.remoteFilesPath, asRoot = enabled)
    }

    fun uploadToRemoteFolder(uri: Uri, context: Context) {
        uploadFile(uri, context, destination = normalizeRemotePath(_uiState.value.remoteFilesPath))
    }

    fun transferRemoteFile(source: String, destination: String, operation: String = "copy") {
        viewModelScope.launch {
            val target = normalizeRemotePath(destination)
            runCatching {
                chatRepository.transferRemoteFile(
                    _uiState.value.agentBaseUrl,
                    source,
                    target,
                    operation,
                    asRoot = _uiState.value.remoteFilesAsRoot,
                    confirm = true
                )
            }.onSuccess {
                _uiState.update { it.copy(remoteFilesError = null) }
                refreshRemoteFiles()
            }.onFailure { error ->
                _uiState.update { it.copy(remoteFilesError = "Transfer failed: ${error.displayMessage("request rejected")}") }
            }
        }
    }

    fun openRemoteFile(entry: RemoteFileEntry) {
        if (entry.isDirectory) {
            refreshRemoteFiles(entry.path)
        } else {
            closeRemoteFiles()
            openFileViewer(entry.path)
        }
    }

    fun deleteFile(filePath: String, asRoot: Boolean = false) {
        viewModelScope.launch {
            val baseUrl = _uiState.value.agentBaseUrl
            val result = chatRepository.deleteFile(baseUrl, filePath, asRoot = asRoot)

            result.onSuccess {
                closeFileViewer()
                val systemMsg = ChatMessage(role = "user", content = "Deleted file '$filePath' from remote PC.")
                _uiState.update { state -> state.copy(messages = state.messages + systemMsg) }
            }.onFailure { error ->
                _uiState.update { state -> state.copy(errorMessage = "Delete error: ${error.localizedMessage}") }
            }
        }
    }

    fun closeFileViewer() {
        _uiState.update { it.copy(filePreviewState = null) }
    }

    fun retryFileDownload() { lastDownloadPath?.let(::openFileViewer) }

    fun askAboutScreenshot() {
        val preview = _uiState.value.filePreviewState ?: return
        val bytes = preview.imageBytes
        if (bytes == null) {
            _uiState.update { it.copy(errorMessage = "Download the screenshot before asking about it.") }
            return
        }
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val existing = _uiState.value.pendingChatImages
        if (existing.size >= 4 || existing.sumOf { it.length.toLong() } + encoded.length > 16L * 1024 * 1024) {
            _uiState.update { it.copy(errorMessage = "Remove an attached image before adding this screenshot.") }
            return
        }
        val visionModel = _uiState.value.visionCapableModels.firstOrNull()
        if (visionModel == null) {
            _uiState.update { it.copy(errorMessage = "The server did not advertise a vision-capable model. Refresh server capabilities and try again.") }
            return
        }
        _uiState.update { state ->
            state.copy(
                pendingChatImages = state.pendingChatImages + encoded,
                inputText = state.inputText.ifBlank { "Describe this Ubuntu desktop screenshot and summarize anything that needs attention." },
                connectionMode = ConnectionMode.AGENT,
                selectedModel = visionModel,
                filePreviewState = null,
                errorMessage = null
            )
        }
        viewModelScope.launch {
            preferencesRepository.saveSelectedModel(visionModel)
            preferencesRepository.saveConnectionMode(ConnectionMode.AGENT)
        }
    }

    fun retryLastMessage() {
        val lastUserMsg = _uiState.value.messages.lastOrNull { it.role == "user" }
        if (lastUserMsg == null || _uiState.value.isStreaming) return
        viewModelScope.launch {
            val pending = preferencesRepository.getPendingAgentChatRequests()
                .lastOrNull { item -> item.request.session_id == _uiState.value.sessionId && item.request.prompt == lastUserMsg.content }
            val pendingRequest = pending?.request
            val currentSession = _uiState.value.sessionId
            val isSameRequest = pending != null && pendingRequest != null &&
                pendingRequest.session_id == currentSession && pendingRequest.prompt == lastUserMsg.content
            _uiState.update { state ->
                state.copy(
                    inputText = lastUserMsg.content,
                    pendingChatImages = if (isSameRequest) pending.request.images else state.pendingChatImages,
                    selectedModel = if (isSameRequest) pending.request.model else state.selectedModel,
                    connectionMode = if (isSameRequest) ConnectionMode.AGENT else state.connectionMode,
                    agentChatRetryKey = if (isSameRequest) pending.idempotencyKey else null,
                    agentChatRetryBaseUrl = if (isSameRequest) pending.baseUrl else null,
                )
            }
        }
    }

    fun reviewLastChanges(messageId: String) {
        if (_uiState.value.isStreaming) return
        val prompt = "Summarize the quality and risks of the changes just made, with file paths and concrete concerns."
        _uiState.update { state ->
            state.copy(
                inputText = prompt,
                selectedModel = "qwen2.5-coder:1.5b",
                connectionMode = ConnectionMode.AGENT,
                agentChatRetryKey = null,
                agentChatRetryBaseUrl = null,
            )
        }
        sendMessage()
    }

    fun deleteChatMessage(messageId: String) {
        viewModelScope.launch {
            val messages = _uiState.value.messages
            val index = messages.indexOfFirst { it.id == messageId }
            if (index < 0) return@launch
            val ids = mutableSetOf(messageId)
            if (messages[index].role == "user") {
                messages.getOrNull(index + 1)?.takeIf { it.role == "assistant" }?.let { ids.add(it.id) }
            } else {
                messages.getOrNull(index - 1)?.takeIf { it.role == "user" }?.let { ids.add(it.id) }
            }
            _uiState.update { state -> state.copy(messages = state.messages.filterNot { it.id in ids }) }
            ids.forEach { runCatching { chatRepository.deleteMessage(it) } }
        }
    }

    fun retryOnStrong(messageId: String) {
        if (_uiState.value.isStreaming) return
        val messages = _uiState.value.messages
        val index = messages.indexOfFirst { it.id == messageId }
        if (index < 0) return
        val prompt = messages.take(index).lastOrNull { it.role == "user" }?.content?.trim()
        if (prompt.isNullOrBlank()) return
        _uiState.update { state ->
            state.copy(
                inputText = prompt,
                selectedModel = "cloud:strong",
                connectionMode = ConnectionMode.AGENT,
                agentChatRetryKey = null,
                agentChatRetryBaseUrl = null,
                forceLegacyChat = false,
            )
        }
        sendMessage()
    }

    fun regenerateLastAssistant() {
        if (_uiState.value.isStreaming) return
        val messages = _uiState.value.messages
        val lastUserIndex = messages.indexOfLast { it.role == "user" }
        if (lastUserIndex < 0 || lastUserIndex == messages.lastIndex) return
        val lastUserMsg = messages[lastUserIndex]
        viewModelScope.launch {
            val pending = preferencesRepository.getPendingAgentChatRequests()
                .lastOrNull { it.request.session_id == _uiState.value.sessionId && it.request.prompt == lastUserMsg.content }
            pending?.let { preferencesRepository.clearPendingAgentChatRequest(it.idempotencyKey) }
            _uiState.update { state ->
                state.copy(
                    messages = state.messages.take(lastUserIndex),
                    inputText = lastUserMsg.content,
                    pendingChatImages = pending?.request?.images ?: state.pendingChatImages,
                    agentChatRetryKey = null,
                    agentChatRetryBaseUrl = null,
                )
            }
            sendMessage()
        }
    }

    fun askAgentToDiagnose(assistantMessageId: String) {
        if (_uiState.value.isStreaming) return
        val messages = _uiState.value.messages
        val failureIndex = messages.indexOfLast { it.id == assistantMessageId }
        if (failureIndex < 0) return
        val failedPrompt = messages.take(failureIndex).lastOrNull { it.role == "user" }?.content.orEmpty()
        val failureText = messages[failureIndex].content.ifBlank { messages[failureIndex].actionSummary }
        val diagnosisPrompt = buildString {
            appendLine("Diagnose this failed remote workstation operation and suggest a safe correction.")
            appendLine("Do not run commands or change files. Explain the cause first.")
            appendLine("Original request: $failedPrompt")
            append("Failure output:\n$failureText")
        }
        updateInputText(diagnosisPrompt)
        sendMessage()
    }

    fun setConnectionMode(mode: ConnectionMode) {
        if (_uiState.value.connectionMode == mode) return

        _uiState.update { it.copy(connectionMode = mode) }
        viewModelScope.launch {
            preferencesRepository.saveConnectionMode(mode)
            if (mode == ConnectionMode.AGENT) fetchBridgeHelp()
            fetchModels()
        }
    }

    fun updateInputText(newText: String) {
        _uiState.update { it.copy(inputText = newText) }
    }

    fun selectModel(model: String) {
        _uiState.update { it.copy(selectedModel = model) }
        viewModelScope.launch {
            preferencesRepository.saveSelectedModel(model)
        }
    }

    fun selectTerminalTheme(theme: String) {
        _uiState.update { it.copy(terminalTheme = theme) }
        viewModelScope.launch { preferencesRepository.saveTerminalTheme(theme) }
    }

    fun executeMacro(macroPrompt: String) {
        if (_uiState.value.isStreaming) return
        if (macroPrompt.contains("system stats", ignoreCase = true)) {
            openSystemStats()
            return
        }
        _uiState.update { it.copy(inputText = macroPrompt) }
        sendMessage()
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank()) return
        val selectedImages = _uiState.value.pendingChatImages
        val state = _uiState.value
        if (selectedImages.isNotEmpty() && state.connectionMode != ConnectionMode.AGENT) {
            _uiState.update { it.copy(errorMessage = "Image prompts require Agent mode.") }
            return
        }
        if (selectedImages.isNotEmpty() && state.visionCapableModels.isNotEmpty() && state.selectedModel !in state.visionCapableModels) {
            _uiState.update { it.copy(errorMessage = "Choose a vision-capable model before sending attached images.") }
            return
        }
        if (_uiState.value.isStreaming || streamingJob?.isActive == true) {
            enqueuePrompt(text, selectedImages)
            _uiState.update { it.copy(inputText = "", pendingChatImages = emptyList(), errorMessage = null) }
            return
        }
        startPrompt(text, selectedImages)
    }

    private fun enqueuePrompt(text: String, images: List<String>) {
        val userMsg = ChatMessage(role = "user", content = text, jobStatus = "queued")
        val assistantId = UUID.randomUUID().toString()
        val placeholder = ChatMessage(
            id = assistantId,
            role = "assistant",
            content = "",
            jobStatus = "queued",
            queuePosition = promptQueue.size + 1,
        )
        promptQueue.addLast(QueuedPrompt(text, images, userMsg, assistantId))
        _uiState.update { state ->
            state.copy(messages = state.messages + userMsg + placeholder, queuedCount = promptQueue.size)
        }
        syncQueueUi()
        persistQueue()
    }

    private fun startNextQueuedPrompt() {
        val next = promptQueue.removeFirstOrNull()
        if (next == null) {
            _uiState.update { it.copy(queuedCount = 0) }
            viewModelScope.launch { runCatching { preferencesRepository.clearQueuedPrompts() } }
            return
        }
        val positions = promptQueue.mapIndexed { index, queued -> queued.assistantId to index + 1 }.toMap()
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { msg ->
                    when {
                        msg.id == next.assistantId -> msg.copy(
                            queuePosition = null, jobStatus = null, isStreaming = true
                        )
                        msg.id == next.userMsg.id -> msg.copy(jobStatus = null)
                        positions.containsKey(msg.id) -> msg.copy(
                            queuePosition = positions[msg.id], jobStatus = "queued"
                        )
                        else -> msg
                    }
                },
                queuedCount = promptQueue.size,
                isStreaming = true,
            )
        }
        syncQueueUi()
        persistQueue()
        startPrompt(next.text, next.images, queuedUser = next.userMsg, queuedAssistantId = next.assistantId)
    }

    private suspend fun restoreQueuedPrompts() {
        val saved = preferencesRepository.getQueuedPrompts()
        if (saved.isEmpty()) return
        val session = _uiState.value.sessionId
        val mine = saved.filter { it.sessionId == session }
        if (mine.isEmpty()) return
        val restored = mine.map { data ->
            val user = ChatMessage(
                id = data.userMsgId.ifBlank { UUID.randomUUID().toString() },
                role = "user", content = data.text, jobStatus = "queued",
            )
            val assistantId = data.assistantId.ifBlank { UUID.randomUUID().toString() }
            QueuedPrompt(data.text, data.images, user, assistantId)
        }
        promptQueue.addAll(restored)
        val placeholders = restored.map { queued ->
            ChatMessage(
                id = queued.assistantId, role = "assistant", content = "",
                jobStatus = "queued", queuePosition = 0,
            )
        }
        val positions = promptQueue.mapIndexed { index, queued -> queued.assistantId to index + 1 }.toMap()
        _uiState.update { state ->
            state.copy(
                messages = state.messages + restored.map { it.userMsg } + placeholders.map { placeholder ->
                    placeholder.copy(queuePosition = positions[placeholder.id])
                },
                queuedCount = promptQueue.size,
            )
        }
        syncQueueUi()
        if (!_uiState.value.isStreaming && streamingJob?.isActive != true) startNextQueuedPrompt()
    }

    private fun startPrompt(
        text: String,
        selectedImages: List<String>,
        queuedUser: ChatMessage? = null,
        queuedAssistantId: String? = null,
    ) {
        val currentState = _uiState.value
        if (selectedImages.isNotEmpty() && currentState.connectionMode != ConnectionMode.AGENT) {
            _uiState.update { it.copy(errorMessage = "Image prompts require Agent mode.") }
            return
        }
        if (selectedImages.isNotEmpty() && currentState.visionCapableModels.isNotEmpty() && currentState.selectedModel !in currentState.visionCapableModels) {
            _uiState.update { it.copy(errorMessage = "Choose a vision-capable model before sending attached images.") }
            return
        }

        val userMsg = queuedUser ?: ChatMessage(role = "user", content = text)
        val messageSessionId = _uiState.value.sessionId
        val assistantMsgId = queuedAssistantId ?: UUID.randomUUID().toString()
        val assistantMsg = ChatMessage(
            id = assistantMsgId,
            role = "assistant",
            content = "",
            thinking = "",
            actionSummary = "",
            queuePosition = null,
            isStreaming = true
        )
        val isQueued = queuedUser != null
        val images = selectedImages
        val requestGeneration = ++activeRequestGeneration

        _uiState.update { state ->
            state.copy(
                messages = if (isQueued) state.messages else state.messages + userMsg + assistantMsg,
                inputText = "",
                pendingChatImages = emptyList(),
                isStreaming = true,
                queuedCount = promptQueue.size,
                errorMessage = null
            )
        }

        val historyForApi = _uiState.value.messages
            .dropLast(1)
            .filter { it.jobStatus != "queued" && it.queuePosition == null && it.content.isNotBlank() }
        val baseUrl = _uiState.value.activeBaseUrl
        val model = _uiState.value.selectedModel
        val systemPrompt = _uiState.value.systemPrompt
        val mode = _uiState.value.connectionMode
        val retryKey = _uiState.value.agentChatRetryKey
        val retryBaseUrl = _uiState.value.agentChatRetryBaseUrl

        streamingJob = viewModelScope.launch {
            var pendingAgentChat: PendingAgentChatRequest? = null
            try {
                var session = chatRepository.getSession(messageSessionId)
                if (session == null) {
                    chatRepository.createSession(messageSessionId, sessionTitleFromPrompt(text))
                    session = chatRepository.getSession(messageSessionId)
                }
                if (session?.title == "New chat" && chatRepository.getMessagesFlow(messageSessionId).first().isEmpty()) {
                    chatRepository.renameSession(messageSessionId, sessionTitleFromPrompt(text))
                }
                chatRepository.saveMessage(messageSessionId, userMsg)
                chatRepository.saveMessage(messageSessionId, assistantMsg)

                if (mode == ConnectionMode.AGENT) {
                    val chatRequest = com.example.mylocalai.model.ChatRequest(
                        session_id = messageSessionId,
                        model = model,
                        prompt = text,
                        tool_mode = if (_uiState.value.delegateLongWork) "full" else "direct",
                        images = images,
                        allowed_tools = _uiState.value.allowedTools.toList().sorted(),
                        language = _uiState.value.language.takeIf { it != LocaleHelper.LANG_SYSTEM }
                            ?: java.util.Locale.getDefault().language,
                    )
                    val requestFingerprint = agentChatRequestFingerprint(chatRequest)
                    val previousRequest = if (retryKey == null) null else preferencesRepository.getPendingAgentChatRequests()
                        .firstOrNull { it.idempotencyKey == retryKey }
                    val pendingRequest = previousRequest?.takeIf {
                        it.baseUrl == retryBaseUrl &&
                            (it.requestFingerprint == requestFingerprint || (it.requestFingerprint.isBlank() && agentChatRequestFingerprint(it.request) == requestFingerprint))
                    }
                        ?: PendingAgentChatRequest(UUID.randomUUID().toString(), baseUrl, chatRequest.copy(images = emptyList()), userMsg.id, requestFingerprint)
                    pendingAgentChat = pendingRequest
                    preferencesRepository.savePendingAgentChatRequest(pendingRequest)

                    val advertisedRoutes = _uiState.value.bridgeRoutes
                    if (_uiState.value.forceLegacyChat || (advertisedRoutes != null && "POST /api/chat/jobs" !in advertisedRoutes)) {
                        // Legacy server without the async job API: use the synchronous chat endpoint.
                        val response = chatRepository.requestAgentChat(
                            pendingRequest.baseUrl,
                            chatRequest,
                            pendingRequest.idempotencyKey,
                        )
                        preferencesRepository.clearPendingAgentChatRequest(pendingRequest.idempotencyKey)
                        monitorActiveJobs()
                        applyAgentChatResponseToMessage(
                            response, assistantMsgId, baseUrl,
                            rememberAgentRequestId(response.request_id.orEmpty()),
                            requestIdForMarker = response.request_id.orEmpty(),
                        )
                    } else {
                        runAgentChatJobFlow(
                            baseUrl = baseUrl,
                            chatRequest = chatRequest,
                            pendingRequest = pendingRequest,
                            assistantMsgId = assistantMsgId,
                            messageSessionId = messageSessionId,
                            userMsg = userMsg,
                            requestGeneration = requestGeneration,
                        )
                    }
                } else {
                    chatRepository.streamChat(baseUrl, model, systemPrompt, historyForApi, mode)
                        .collect { chunk ->
                        _uiState.update { currentState ->
                            val updatedMessages = currentState.messages.map { msg ->
                                if (msg.id == assistantMsgId) {
                                    val newContent = msg.content + chunk.content
                                    val newThinking = msg.thinking + chunk.thinking
                                    val newAction = if (chunk.actionSummary.isNotBlank()) {
                                        if (msg.actionSummary.isBlank()) chunk.actionSummary
                                        else "${msg.actionSummary}\n${chunk.actionSummary}"
                                    } else {
                                        msg.actionSummary
                                    }

                                    msg.copy(
                                        content = newContent,
                                        thinking = newThinking,
                                        actionSummary = newAction,
                                        isStreaming = !chunk.isDone
                                    )
                                } else {
                                    msg
                                }
                            }
                            currentState.copy(
                                messages = updatedMessages,
                                isStreaming = !chunk.isDone
                            )
                        }
                    }
                }
                if (!AppVisibility.isForeground && _uiState.value.notifyReplies) {
                    val answer = _uiState.value.messages.firstOrNull { it.id == assistantMsgId }?.content.orEmpty()
                    if (answer.isNotBlank()) {
                        AgentNotifications.notifyReply(
                            getApplication(),
                            messageSessionId,
                            text,
                            answer,
                            requestId = _uiState.value.lastChatJobId,
                            baseUrl = _uiState.value.agentBaseUrl,
                        )
                    }
                }
            } catch (e: CancellationException) {
                _uiState.update { currentState ->
                    val updatedMessages = currentState.messages.map { msg ->
                        if (msg.id == assistantMsgId) msg.copy(isStreaming = false) else msg
                    }
                    currentState.copy(messages = updatedMessages, isStreaming = if (requestGeneration == activeRequestGeneration) false else currentState.isStreaming)
                }
            } catch (e: Exception) {
                val isTimeout = e is java.net.SocketTimeoutException ||
                    e is java.util.concurrent.TimeoutException ||
                    e.cause is java.net.SocketTimeoutException ||
                    e.message?.contains("timeout", ignoreCase = true) == true
                pendingAgentChat?.let { pending ->
                    val code = (e as? HttpException)?.code()
                    // Never automatically replay timed out requests or 4xx client errors
                    if (isTimeout || (code != null && code in 400..499 && code != 409)) {
                        runCatching { preferencesRepository.clearPendingAgentChatRequest(pending.idempotencyKey) }
                        _uiState.update { it.copy(agentChatRetryKey = null, agentChatRetryBaseUrl = null) }
                    }
                }
                val errorBody = (e as? HttpException)?.response()?.errorBody()?.string().orEmpty()
                val errorJson = runCatching { JSONObject(errorBody) }.getOrNull()
                val requestId = errorJson?.optString("request_id")?.takeIf(String::isNotBlank)
                val serverDetail = listOf("detail", "message", "error").firstNotNullOfOrNull { key ->
                    errorJson?.optString(key)?.takeIf { it.isNotBlank() && it != "null" }
                }
                _uiState.update { currentState ->
                    val updatedMessages = currentState.messages.map { msg ->
                        if (msg.id == assistantMsgId) {
                            val errorContent = if (isTimeout) {
                                "[Request Timed Out] The request timed out before receiving a response from the workstation. Remote commands or scripts may already have executed on the PC. Automatic resend is blocked to avoid side effects. Inspect Server logs or PC state before retrying."
                            } else {
                                buildString {
                                    append("[Connection Error] ").append(serverDetail ?: e.displayMessage("Unable to reach the workstation."))
                                    requestId?.let { append("\nRequest ID: ").append(it) }
                                }
                            }
                            msg.copy(
                                content = listOf(msg.content, errorContent).filter { it.isNotBlank() }.joinToString("\n"),
                                isStreaming = false,
                                isJobFailure = true
                            )
                        } else {
                            msg
                        }
                    }
                    currentState.copy(messages = updatedMessages, isStreaming = if (requestGeneration == activeRequestGeneration) false else currentState.isStreaming)
                }
            } finally {
                _uiState.update { currentState ->
                    val updatedMessages = currentState.messages.map { msg ->
                        if (msg.id == assistantMsgId) msg.copy(isStreaming = false) else msg
                    }
                    currentState.copy(messages = updatedMessages, isStreaming = if (requestGeneration == activeRequestGeneration) false else currentState.isStreaming)
                }
                if (requestGeneration == activeRequestGeneration) streamingJob = null
                withContext(NonCancellable) {
                    _uiState.value.messages.firstOrNull { it.id == assistantMsgId }?.let { message ->
                        runCatching { chatRepository.saveMessage(messageSessionId, message.copy(isStreaming = false)) }
                    }
                }
                if (requestGeneration == activeRequestGeneration && promptQueue.isNotEmpty()) {
                    startNextQueuedPrompt()
                }
            }
        }
    }

    private suspend fun applyAgentChatResponseToMessage(
        response: ChatResponse,
        assistantMsgId: String,
        baseUrl: String,
        isFirstResponse: Boolean,
        requestIdForMarker: String = "",
    ) {
        val taskOffers = extractSoftwareTaskOffers(response)
        if (isFirstResponse && taskOffers.isNotEmpty()) {
            registerSoftwareTaskOffers(taskOffers, baseUrl)
        }
        val assistantText = response.assistant_text?.takeIf { it.isNotBlank() }
            ?: response.message?.content.orEmpty()
        val failedTools = response.tool_results.filter { result ->
            !result.error.isNullOrBlank() || result.timed_out == true || (result.exit_code != null && result.exit_code != 0)
        }
        val toolFailureSummary = failedTools.joinToString("\n") { result ->
            val reason = result.error?.takeIf(String::isNotBlank)
                ?: if (result.timed_out == true) "timed out" else "exit code ${result.exit_code}"
            "${result.tool}: $reason"
        }
        val queuedTasksSummary = taskOffers.joinToString { task -> "${task.task_id} (${task.status})" }
        val finalText = if (!isFirstResponse) {
            "[Duplicate response suppressed] This request ID was already handled."
        } else when {
            response.execution_status.equals("not_executed", ignoreCase = true) -> {
                // Never show the model's narrated "changes" when no change tool actually ran.
                val reason = response.error?.takeIf { it.isNotBlank() }
                    ?: "The model did not invoke a change tool."
                "[Not executed] No PC changes were made. $reason"
            }
            response.execution_status.equals("task_failed", ignoreCase = true) ->
                "[Software task failed] ${response.error?.takeIf(String::isNotBlank) ?: assistantText}".trim()
            response.execution_status.equals("task_queued", ignoreCase = true) || taskOffers.isNotEmpty() ->
                "${assistantText.ifBlank { "Software task queued." }}\n\n[Task queued] ${queuedTasksSummary.ifBlank { "Task status is pending." }}. This is not completion; review progress and verification in Software tasks.".trim()
            failedTools.isNotEmpty() ->
                "[Tool failure] The server returned unsuccessful tool results; this request is not reported as successful.\n$toolFailureSummary\n\n$assistantText".trim()
            assistantText.isNotBlank() && !response.error.isNullOrBlank() &&
                !assistantText.contains(response.error!!, ignoreCase = true) -> "$assistantText\n\n${response.error}"
            assistantText.isNotBlank() -> assistantText
            !response.error.isNullOrBlank() -> "[Error] ${response.error}"
            else -> ""
        }
        val responseDetails = if (!isFirstResponse) {
            "Request ID: ${response.request_id}\nDuplicate response ignored to prevent showing the same result twice."
        } else buildList {
            response.request_id?.takeIf { it.isNotBlank() }?.let { add("Request ID: $it") }
            if (response.actions.isNotEmpty()) add("Actions: ${response.actions.joinToString()}")
            if (response.execution_status?.isNotBlank() == true) add("Execution status: ${response.execution_status}")
            if (failedTools.isNotEmpty()) add("Tool failures:\n$toolFailureSummary")
            taskOffers.forEach { task ->
                add("Software task: ${task.task_id} · ${task.status} · phase ${task.phase.ifBlank { "—" }}${if (task.reused) " · reused" else ""}")
            }
            val queueWait = response.ollama?.get("ollama_queue_wait_ms")
                ?.takeUnless { it.isJsonNull } ?: response.ollama_queue_wait_ms?.takeUnless { it.isJsonNull }
            queueWait?.let { add("Ollama queue wait: ${it.asString} ms") }
            response.ollama?.takeIf { it.size() > 0 }?.let { add("Ollama metrics: $it") }
        }.joinToString("\n")
        _uiState.update { currentState ->
            currentState.copy(
                messages = currentState.messages.map { message ->
                    if (message.id == assistantMsgId) message.copy(
                        content = AgentAnswerSanitizer.sanitize(finalText),
                        actionSummary = if (isFirstResponse && requestIdForMarker.isNotBlank())
                            appendLine(responseDetails, "Response rendered: $requestIdForMarker")
                        else responseDetails,
                        toolResults = if (isFirstResponse) response.tool_results else emptyList(),
                        isStreaming = false,
                        isJobFailure = isFirstResponse && (failedTools.isNotEmpty() ||
                            response.execution_status.equals("task_failed", ignoreCase = true)),
                        jobStatus = null,
                        delegatedTaskId = null,
                        delegatedSummary = null,
                    ) else message
                },
                isStreaming = false,
                agentChatRetryKey = null,
                agentChatRetryBaseUrl = null,
            )
        }
    }

    private suspend fun runAgentChatJobFlow(
        baseUrl: String,
        chatRequest: com.example.mylocalai.model.ChatRequest,
        pendingRequest: PendingAgentChatRequest,
        assistantMsgId: String,
        messageSessionId: String,
        userMsg: ChatMessage,
        requestGeneration: Long,
        resume: PersistedChatJob? = null,
    ) {
        var requestId = ""
        try {
            val created = if (resume != null) {
                AsyncChatJobCreated(request_id = resume.requestId, status = "running")
            } else {
                createAgentChatJobSafely(baseUrl, chatRequest, pendingRequest.idempotencyKey, assistantMsgId)
                    ?: return
            }
            requestId = created.request_id
            activeAgentChatJobId = requestId
            if (resume == null) {
                preferencesRepository.saveActiveChatJob(
                    PersistedChatJob(
                        requestId = requestId,
                        sessionId = messageSessionId,
                        assistantMessageId = assistantMsgId,
                        baseUrl = baseUrl,
                        idempotencyKey = pendingRequest.idempotencyKey,
                        startedAtMs = System.currentTimeMillis(),
                        prompt = chatRequest.prompt,
                    )
                )
            }
            // Background completion: if the app is killed while the job runs, WorkManager
            // finishes watching it and posts the reply notification when it terminates.
            ChatJobCompletionMonitor.enqueue(getApplication(), baseUrl, requestId, messageSessionId, chatRequest.prompt)
            _uiState.update { state ->
                state.copy(messages = state.messages.map { msg ->
                    if (msg.id == assistantMsgId) msg.copy(
                        content = "",
                        jobStatus = created.status,
                        isStreaming = true,
                        actionSummary = appendLine(msg.actionSummary, "Job request ID: $requestId"),
                    ) else msg
                })
            }
            val sseEvents = Channel<AsyncChatJobEvent>(Channel.UNLIMITED)
            val seenAsyncEventIds = mutableSetOf<Long>()
            val sseTerminal = java.util.concurrent.atomic.AtomicBoolean(false)
            val sseJob = startAgentChatJobEventStream(baseUrl, requestId, sseEvents, sseTerminal) {
                appendAsyncEventLine(assistantMsgId, AsyncChatJobEvent(0, "", "Live event stream interrupted — continuing with polling."), seenAsyncEventIds)
            }
            try {
                var eventCursor = resume?.eventCursor ?: 0L
                var lastTokenEventId = 0L
                var consecutiveFailures = 0
                var localElapsedBaseMs = resume?.startedAtMs?.takeIf { it > 0 }
                while (true) {
                    // Offline: pause instead of burning the failure budget; resume when back.
                    val monitor = (getApplication<Application>() as? MyLocalAIApplication)?.networkMonitor
                    if (monitor != null && !monitor.isOnline.value) {
                        _uiState.update { it.copy(isAwaitingNetwork = true) }
                        updateAsyncAssistantText(assistantMsgId, "Waiting for internet…", isStreaming = true)
                        monitor.isOnline.first { it }
                        _uiState.update { it.copy(isAwaitingNetwork = false) }
                        consecutiveFailures = 0
                        continue
                    }
                    // Drain live SSE progress events.
                    while (true) {
                        val event = sseEvents.tryReceive().getOrNull() ?: break
                        if (event.event_type == "token") {
                            // SSE replays from the beginning on reconnect; ignore replayed tokens.
                            if (event.event_id > lastTokenEventId) {
                                lastTokenEventId = event.event_id
                                appendTokenToAssistant(assistantMsgId, event.message)
                            }
                        } else {
                            appendAsyncEventLine(assistantMsgId, event, seenAsyncEventIds)
                        }
                    }
                    val status = try {
                        chatRepository.getAgentChatJob(baseUrl, requestId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        consecutiveFailures++
                        val detail = (e as? HttpException)?.let(::httpErrorDetail) ?: e.displayMessage("Status poll failed.")
                        if (e is HttpException && e.code() == 404) {
                            showAsyncError(assistantMsgId, "[Error] The agent job was not found on the server: $detail")
                            break
                        }
                        if (e is HttpException && e.code() == 429) {
                            appendAsyncEventLine(assistantMsgId, AsyncChatJobEvent(0, "", "The agent job queue is full; try again in a moment."), seenAsyncEventIds)
                        }
                        if (consecutiveFailures >= 10) {
                            showAsyncError(assistantMsgId, "[Error] Could not retrieve the agent job status after several attempts: $detail")
                            break
                        }
                        delay((5_000L + consecutiveFailures.toLong() * 2_000L).coerceAtMost(30_000L))
                        continue
                    }
                    consecutiveFailures = 0
                    if (localElapsedBaseMs == null && status.status in setOf("queued", "running")) {
                        localElapsedBaseMs = System.currentTimeMillis()
                    }
                    // The bridge reports elapsed_seconds only at terminal states; while active it stays 0,
                    // so tick locally to keep the progress/dashboard timer honest.
                    val effectiveElapsed = when {
                        status.elapsed_seconds != null && status.elapsed_seconds > 0 -> status.elapsed_seconds
                        localElapsedBaseMs != null -> (System.currentTimeMillis() - localElapsedBaseMs) / 1000.0
                        else -> null
                    }
                    _uiState.update { it.copy(activeChatJobElapsedSeconds = effectiveElapsed?.toLong()) }
                    when (status.status) {
                        "completed" -> {
                            removeDuplicateJobMessages(requestId, assistantMsgId)
                            _uiState.update { it.copy(lastChatJobId = requestId) }
                            preferencesRepository.clearPendingAgentChatRequest(pendingRequest.idempotencyKey)
                            monitorActiveJobs()
                            val response = status.response ?: ChatResponse()
                            Log.d("MyLocalAI", "chat job $requestId completed: ${response.execution_status}")
                            val responseRequestId = status.request_id.ifBlank { response.request_id.orEmpty() }
                            applyAgentChatResponseToMessage(
                                response, assistantMsgId, baseUrl,
                                rememberAgentRequestId(responseRequestId),
                                requestIdForMarker = responseRequestId,
                            )
                            _uiState.update { it.copy(agentChatRetryKey = null, agentChatRetryBaseUrl = null) }
                            break
                        }
                        "delegated" -> {
                            removeDuplicateJobMessages(requestId, assistantMsgId)
                            preferencesRepository.clearPendingAgentChatRequest(pendingRequest.idempotencyKey)
                            val response = status.response ?: ChatResponse()
                            val offers = extractSoftwareTaskOffers(response)
                            val taskId = status.task_id?.takeIf(String::isNotBlank) ?: offers.firstOrNull()?.task_id
                            if (offers.isNotEmpty()) {
                                registerSoftwareTaskOffers(offers, baseUrl)
                            } else {
                                taskId?.let {
                                    registerSoftwareTaskOffers(listOf(SoftwareTaskOffer(task_id = it, status = "running", phase = "delegated")), baseUrl)
                                }
                            }
                            val responseText = AgentAnswerSanitizer.sanitize(
                                response.message?.content?.takeIf(String::isNotBlank) ?: response.assistant_text.orEmpty()
                            )
                            updateAsyncAssistantText(assistantMsgId, responseText, isStreaming = false)
                            val taskSummary = offers.firstOrNull()
                            val delegatedSummary = taskSummary?.let { offer ->
                                "Status: ${offer.status} · Phase: ${offer.phase.ifBlank { "—" }}"
                            } ?: taskId.orEmpty()
                            _uiState.update { state ->
                                state.copy(messages = state.messages.map { msg ->
                                    if (msg.id == assistantMsgId) msg.copy(
                                        delegatedTaskId = taskId,
                                        delegatedSummary = delegatedSummary,
                                        jobStatus = null,
                                    ) else msg
                                })
                            }
                            _uiState.update { it.copy(agentChatRetryKey = null, agentChatRetryBaseUrl = null) }
                            break
                        }
                        "failed", "cancelled", "interrupted" -> {
                            removeDuplicateJobMessages(requestId, assistantMsgId)
                            // Any terminal failure is final: rotate the key so an explicit retry
                            // creates a NEW job instead of re-polling the same dead request.
                            preferencesRepository.clearPendingAgentChatRequest(pendingRequest.idempotencyKey)
                            _uiState.update { it.copy(agentChatRetryKey = null, agentChatRetryBaseUrl = null) }
                            val response = status.response
                            val errorText = status.error?.takeIf(String::isNotBlank)
                                ?: response?.error?.takeIf(String::isNotBlank)
                                ?: response?.message?.content?.takeIf(String::isNotBlank)
                                ?: "The agent job ended with status '${status.status}'."
                            Log.w("MyLocalAI", "chat job $requestId ${status.status}: $errorText")
                            val label = when (status.status) {
                                "failed" -> "The agent job failed"
                                "cancelled" -> "The agent job was cancelled"
                                else -> "The agent job was interrupted"
                            }
                            updateAsyncAssistantText(assistantMsgId, "[Error] $label: $errorText", isStreaming = false, isFailure = true)
                            break
                        }
                        else -> {
                            updateAsyncJobProgress(assistantMsgId, status.status, effectiveElapsed?.toLong(), isStreaming = true)
                            try {
                                val page = chatRepository.getAgentChatJobEvents(baseUrl, requestId, eventCursor)
                                page.events.filter { it.event_id > eventCursor }.forEach { event ->
                                    if (event.event_type == "token") {
                                        if (event.event_id > lastTokenEventId) {
                                            lastTokenEventId = event.event_id
                                            appendTokenToAssistant(assistantMsgId, event.message)
                                        }
                                    } else {
                                        appendAsyncEventLine(assistantMsgId, event, seenAsyncEventIds)
                                    }
                                }
                                if (page.next_after_id > eventCursor) {
                                    eventCursor = page.next_after_id
                                    preferencesRepository.saveActiveChatJob(
                                        PersistedChatJob(
                                            requestId, messageSessionId, assistantMsgId, baseUrl,
                                            pendingRequest.idempotencyKey,
                                            localElapsedBaseMs ?: System.currentTimeMillis(),
                                            eventCursor, chatRequest.prompt,
                                        )
                                    )
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                // Event polling is best-effort while the job is active.
                            }
                            if (sseTerminal.get()) {
                                // The stream reported terminal — poll immediately instead of waiting.
                                delay(400L)
                                continue
                            }
                            delay(2_500L)
                        }
                    }
                }
            } finally {
                sseJob.cancel()
            }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                if (requestId.isNotBlank()) {
                    // Do NOT cancel the server job: the phone losing track of a long job
                    // must not kill work already running on the PC. Reopening the chat
                    // reattaches to the persisted job via its request id.
                    _uiState.update { state ->
                        state.copy(
                            messages = state.messages.map { msg ->
                                if (msg.id == assistantMsgId) msg.copy(
                                    content = "[Paused] " + getApplication<Application>().getString(R.string.chip_paused),
                                    isStreaming = false
                                ) else msg
                            },
                            isStreaming = false
                        )
                    }
                }
            }
        } catch (e: Exception) {
            showAsyncError(assistantMsgId, "[Error] ${e.displayMessage("The agent job could not be tracked.")}")
        } finally {
            if (requestGeneration == activeRequestGeneration) streamingJob = null
            if (requestId.isNotBlank() && activeAgentChatJobId == requestId) {
                activeAgentChatJobId = null
                preferencesRepository.clearActiveChatJob()
            }
            withContext(NonCancellable) {
                _uiState.value.messages.firstOrNull { it.id == assistantMsgId }?.let { message ->
                    runCatching { chatRepository.saveMessage(messageSessionId, message.copy(isStreaming = false)) }
                }
            }
            refreshSessionTaskInfo(messageSessionId)
        }
    }

    private suspend fun createAgentChatJobSafely(
        baseUrl: String,
        chatRequest: com.example.mylocalai.model.ChatRequest,
        idempotencyKey: String,
        assistantMsgId: String,
    ): AsyncChatJobCreated? {
        var attempts = 0
        while (true) {
            attempts++
            try {
                val created = chatRepository.createAgentChatJob(baseUrl, chatRequest, idempotencyKey)
                if (created.request_id.isBlank()) {
                    showAsyncError(assistantMsgId, "[Error] The server returned a job creation response without a request_id.")
                    return null
                }
                return created
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                when (e.code()) {
                    429 -> {
                        showAsyncError(assistantMsgId, "The agent job queue is full. Try again in a moment.")
                        return null
                    }
                    409 -> {
                        showAsyncError(assistantMsgId, "[Error] The job key was reused for a different request; the job was not created: ${httpErrorDetail(e)}")
                        return null
                    }
                    503 -> {
                        if (attempts <= 3) {
                            delay(3_000L * attempts.toLong())
                            continue
                        }
                        showAsyncError(assistantMsgId, "Storage is busy on the server. Try again in a moment.")
                        return null
                    }
                    else -> {
                        showAsyncError(assistantMsgId, "[Error] ${httpErrorDetail(e)}")
                        return null
                    }
                }
            } catch (e: Exception) {
                val isTimeout = e is java.net.SocketTimeoutException ||
                    e is java.util.concurrent.TimeoutException ||
                    e.cause is java.net.SocketTimeoutException ||
                    e.message?.contains("timeout", ignoreCase = true) == true
                if (isTimeout) {
                    // A timed-out send may already have executed remotely — never auto-replay.
                    showAsyncError(assistantMsgId, "[Error] The request timed out. Check the PC before retrying.")
                    return null
                }
                // Network drop: hold the send, retry on reconnect with the SAME key (server dedupes).
                if (!awaitNetwork(assistantMsgId)) {
                    showAsyncError(assistantMsgId, "[Error] Could not reach the workstation to start the job: ${e.displayMessage("Connection error.")}")
                    return null
                }
                attempts++
                // Back off even when "online" — the bridge itself may be unreachable (Tailscale off),
                // otherwise a failing POST would spin in a tight loop.
                delay((1_000L * attempts).coerceAtMost(10_000L))
                continue
            }
        }
    }

    private suspend fun awaitNetwork(assistantMsgId: String): Boolean {
        val monitor = (getApplication<Application>() as? MyLocalAIApplication)?.networkMonitor
            ?: return false
        if (monitor.isOnline.value) return true
        _uiState.update { it.copy(isAwaitingNetwork = true) }
        updateAsyncAssistantText(assistantMsgId, "Waiting for internet…", isStreaming = true)
        monitor.isOnline.first { it }
        _uiState.update { it.copy(isAwaitingNetwork = false) }
        updateAsyncJobProgress(assistantMsgId, "queued", null, isStreaming = true)
        return true
    }

    private fun jobStatusLine(status: String): String = when (status.lowercase()) {
        "queued" -> "Waiting…"
        else -> "Working…"
    }

    private fun elapsedSuffix(elapsedSeconds: Double?): String {
        val seconds = elapsedSeconds?.takeIf { it.isFinite() }?.roundToLong()?.coerceAtLeast(0) ?: return ""
        return if (seconds >= 60) " · ${seconds / 60}m ${seconds % 60}s" else " · ${seconds}s"
    }

    private fun startAgentChatJobEventStream(
        baseUrl: String,
        requestId: String,
        channel: Channel<AsyncChatJobEvent>,
        terminalFlag: java.util.concurrent.atomic.AtomicBoolean,
        onStreamLost: () -> Unit,
    ): Job {
        return viewModelScope.launch(Dispatchers.IO) {
            var afterId = 0L
            var backoffMs = 1_000L
            var lossNotified = false
            while (isActive && !terminalFlag.get()) {
                try {
                    val result = chatRepository.streamAgentChatJobEvents(
                        baseUrl,
                        requestId,
                        afterId = afterId,
                        onEvent = { event ->
                            channel.trySend(event)
                            if (event.event_id > afterId) afterId = event.event_id
                        },
                        onTerminalStatus = { terminalFlag.set(true) },
                    )
                    if (result.isSuccess || terminalFlag.get()) break
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Stream dropped — reconnect below; JSON polling remains the authority.
                }
                if (terminalFlag.get() || !isActive) break
                if (!lossNotified) {
                    lossNotified = true
                    onStreamLost()
                }
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
            }
        }
    }

    private suspend fun removeDuplicateJobMessages(requestId: String, keepMessageId: String) {
        if (requestId.isBlank()) return
        val duplicates = _uiState.value.messages.filter { msg ->
            msg.role == "assistant" && msg.id != keepMessageId &&
                msg.actionSummary.lineSequence().any { it.trim() == "Job request ID: $requestId" }
        }
        if (duplicates.isEmpty()) return
        duplicates.forEach { msg -> runCatching { chatRepository.deleteMessage(msg.id) } }
        val ids = duplicates.mapTo(mutableSetOf()) { it.id }
        _uiState.update { state -> state.copy(messages = state.messages.filterNot { it.id in ids }) }
    }

    private fun updateAsyncJobProgress(
        assistantMsgId: String,
        status: String,
        elapsedSeconds: Long?,
        isStreaming: Boolean,
    ) {
        _uiState.update { state ->
            state.copy(
                isStreaming = isStreaming,
                messages = state.messages.map { msg ->
                    if (msg.id == assistantMsgId) msg.copy(
                        isStreaming = isStreaming,
                        jobStatus = status,
                        elapsedSeconds = elapsedSeconds,
                        isJobFailure = false,
                    ) else msg
                },
            )
        }
    }

    private fun updateAsyncAssistantText(
        assistantMsgId: String,
        text: String,
        isStreaming: Boolean,
        isFailure: Boolean = false,
    ) {
        _uiState.update { state ->
            state.copy(
                isStreaming = isStreaming,
                messages = state.messages.map { msg ->
                    if (msg.id == assistantMsgId) msg.copy(
                        content = text,
                        isStreaming = isStreaming,
                        isJobFailure = isFailure,
                        jobStatus = if (isFailure) "failed" else msg.jobStatus,
                    ) else msg
                },
            )
        }
    }

    private fun showAsyncError(assistantMsgId: String, text: String) {
        updateAsyncAssistantText(assistantMsgId, text, isStreaming = false, isFailure = true)
    }

    private fun appendLine(existing: String, line: String): String =
        if (existing.isBlank()) line else existing + "\n" + line

    private fun appendTokenToAssistant(assistantMsgId: String, token: String) {
        if (token.isEmpty()) return
        _uiState.update { state ->
            state.copy(messages = state.messages.map { msg ->
                if (msg.id == assistantMsgId) msg.copy(content = msg.content + token) else msg
            })
        }
    }

    private fun appendAsyncEventLine(assistantMsgId: String, event: AsyncChatJobEvent, seenIds: MutableSet<Long>) {
        if (event.event_id > 0 && !seenIds.add(event.event_id)) return
        // Streamed answer fragments belong in the message body, never in the action summary.
        if (event.event_type == "token") return
        // Heartbeats and job lifecycle lines are noise in the tool log. Heartbeats only
        // update the single progress header; tool/verification events stay in the log.
        if (event.event_type in setOf("model_step", "accepted", "started", "completed")) {
            if (event.event_type == "model_step") {
                val heartbeat = event.message.takeIf(String::isNotBlank)?.take(160)
                if (heartbeat != null) {
                    _uiState.update { state ->
                        state.copy(messages = state.messages.map { msg ->
                            if (msg.id == assistantMsgId) msg.copy(latestEvent = heartbeat) else msg
                        })
                    }
                }
            }
            return
        }
        val message = event.message.ifBlank {
            event.data?.takeIf { it.isJsonPrimitive && !it.isJsonNull }?.asString?.takeIf(String::isNotBlank).orEmpty()
        }
        if (message.isBlank() && event.event_type.isBlank()) return
        val label = when (event.event_type) {
            "accepted" -> "Job accepted"
            "started" -> "Job started"
            "model_step" -> "Model step"
            "tool_started" -> "Tool started"
            "tool_result" -> "Tool finished"
            "verification_started" -> "Verifying"
            "verification_result" -> "Verification result"
            "completed" -> "Job completed"
            "delegated" -> "Job delegated"
            "failed" -> "Job failed"
            "cancelled" -> "Job cancelled"
            "interrupted" -> "Job interrupted"
            else -> null
        }
        val text = when {
            event.event_type == "model_step" -> message.takeIf(String::isNotBlank)
            label != null && message.isBlank() -> label
            label != null && message.isNotBlank() -> {
                if (event.event_type in setOf("tool_started", "tool_result") &&
                    (message.startsWith("Preparing ", ignoreCase = true) ||
                     message.startsWith("Running ", ignoreCase = true) ||
                     message.startsWith("Finished ", ignoreCase = true))) {
                    message
                } else {
                    "$label: $message"
                }
            }
            message.isNotBlank() -> message
            else -> null
        } ?: return
        _uiState.update { state ->
            state.copy(messages = state.messages.map { msg ->
                if (msg.id == assistantMsgId && msg.actionSummary.lines().none { it.trim() == text }) {
                    val updatedLatest = if (event.event_type == "model_step") {
                        if (msg.latestEvent.isNullOrBlank() || msg.latestEvent?.startsWith("Working") == true) {
                            message.take(160)
                        } else {
                            msg.latestEvent
                        }
                    } else {
                        message.takeIf(String::isNotBlank)?.take(160) ?: msg.latestEvent
                    }
                    msg.copy(
                        actionSummary = appendLine(msg.actionSummary, text),
                        latestEvent = updatedLatest,
                    )
                } else msg
            })
        }
    }

    fun stopStreaming() {
        activeRequestGeneration += 1
        streamingJob?.cancel()
        streamingJob = null
        promptQueue.clear()
        viewModelScope.launch { runCatching { preferencesRepository.clearQueuedPrompts() } }
        _uiState.update { currentState ->
            val updatedMessages = currentState.messages.map { msg ->
                when {
                    msg.queuePosition != null -> msg.copy(
                        queuePosition = null,
                        content = "[Cancelled: this prompt was still queued]",
                        isStreaming = false
                    )
                    msg.jobStatus == "queued" -> msg.copy(jobStatus = null)
                    msg.isStreaming -> msg.copy(isStreaming = false)
                    else -> msg
                }
            }
            currentState.copy(messages = updatedMessages, isStreaming = false, queuedCount = 0)
        }
        syncQueueUi()
    }

    fun clearHistory() {
        createNewSession()
    }

    fun createNewSession(firstPrompt: String? = null) {
        stopStreaming()
        sessionMessagesJob?.cancel()
        val id = UUID.randomUUID().toString()
        val title = firstPrompt?.takeIf(String::isNotBlank)?.let(::sessionTitleFromPrompt) ?: "New chat"
        _uiState.update { it.copy(sessionId = id, messages = emptyList()) }
        sessionMessagesJob = viewModelScope.launch {
            chatRepository.createSession(id, title)
            preferencesRepository.saveActiveSessionId(id)
            chatRepository.getMessagesFlow(id).collect { messages ->
                val state = _uiState.value
                if (state.sessionId == id && !state.isStreaming) _uiState.update { it.copy(messages = messages) }
            }
        }
    }

    fun updateSessionSearch(query: String) {
        _uiState.update { it.copy(sessionSearchQuery = query) }
        sessionSearchJob?.cancel()
        sessionSearchJob = viewModelScope.launch {
            chatRepository.searchSessionsFlow(query).collect { sessions ->
                _uiState.update { it.copy(sessions = sessions) }
            }
        }
    }

    fun switchSession(sessionId: String) {
        if (sessionId.isBlank()) return
        streamingJob?.cancel()
        streamingJob = null
        sessionMessagesJob?.cancel()
        _uiState.update { it.copy(sessionId = sessionId, messages = emptyList(), isStreaming = false) }
        refreshSessionTaskInfo(sessionId)
        viewModelScope.launch {
            val job = preferencesRepository.getActiveChatJob()
            if (job?.sessionId == sessionId && streamingJob?.isActive != true) restoreActiveChatJob()
        }
        sessionMessagesJob = viewModelScope.launch {
            if (chatRepository.getSession(sessionId) == null) return@launch
            preferencesRepository.saveActiveSessionId(sessionId)
            chatRepository.getMessagesFlow(sessionId).collect { messages ->
                if (_uiState.value.sessionId == sessionId && !_uiState.value.isStreaming) {
                    _uiState.update { state ->
                        val queued = state.messages.filter {
                            it.queuePosition != null || it.jobStatus == "queued"
                        }
                        state.copy(messages = messages + queued)
                    }
                }
            }
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            val wasActive = _uiState.value.sessionId == sessionId
            if (wasActive) {
                streamingJob?.cancel()
                sessionMessagesJob?.cancel()
            }
            chatRepository.deleteSession(sessionId)
            if (wasActive) {
                val fallback = chatRepository.getAllSessions().firstOrNull()
                if (fallback != null) switchSession(fallback.sessionId) else createNewSession()
            }
        }
    }

    fun requestClearCurrentConversation() {
        _uiState.update { it.copy(showClearConversationDialog = true) }
    }

    fun dismissClearConversationDialog() {
        _uiState.update { it.copy(showClearConversationDialog = false) }
    }

    fun requestClearAllChats() {
        _uiState.update { it.copy(showClearAllChatsDialog = true) }
    }

    fun dismissClearAllChatsDialog() {
        _uiState.update { it.copy(showClearAllChatsDialog = false) }
    }

    fun dismissClearChatError() {
        _uiState.update { it.copy(clearChatError = null, failedClearSessionId = null, failedClearSessionIds = emptyList()) }
    }

    fun clearCurrentConversation() {
        val sessionId = _uiState.value.sessionId
        if (sessionId.isBlank()) return
        dismissClearConversationDialog()
        if (_uiState.value.isStreaming) {
            _uiState.update { it.copy(clearChatError = "Wait for streaming to finish before clearing conversation.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isClearingChat = true, clearChatError = null, failedClearSessionId = null) }
            val baseUrl = _uiState.value.agentBaseUrl
            try {
                val response = chatRepository.resetServerSession(baseUrl, sessionId)
                if (response.session_id == sessionId && response.status.equals("cleared", ignoreCase = true)) {
                    chatRepository.clearMessagesForSession(sessionId)
                    _uiState.update { state ->
                        state.copy(
                            messages = if (state.sessionId == sessionId) emptyList() else state.messages,
                            isClearingChat = false,
                            clearChatError = null,
                            failedClearSessionId = null,
                            agentChatRetryKey = null,
                            agentChatRetryBaseUrl = null
                        )
                    }
                } else {
                    val statusText = response.status.ifBlank { "unknown" }
                    val errorMsg = "Server reset returned status '$statusText'. Server history may remain."
                    _uiState.update { state ->
                        state.copy(
                            isClearingChat = false,
                            clearChatError = errorMsg,
                            failedClearSessionId = sessionId
                        )
                    }
                }
            } catch (e: Exception) {
                val detail = (e as? HttpException)?.let(::httpErrorDetail) ?: e.displayMessage("Connection failed")
                val errorMsg = "Could not clear conversation on server ($detail). Server history may remain."
                _uiState.update { state ->
                    state.copy(
                        isClearingChat = false,
                        clearChatError = errorMsg,
                        failedClearSessionId = sessionId
                    )
                }
            }
        }
    }

    fun retryClearCurrentConversation() {
        clearCurrentConversation()
    }

    fun clearAllChats() {
        dismissClearAllChatsDialog()
        if (_uiState.value.isStreaming) {
            _uiState.update { it.copy(clearChatError = "Wait for streaming to finish before clearing chats.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isClearingChat = true, clearChatError = null, failedClearSessionIds = emptyList()) }
            val sessions = chatRepository.getAllSessions()
            if (sessions.isEmpty()) {
                _uiState.update { it.copy(isClearingChat = false) }
                return@launch
            }
            val baseUrl = _uiState.value.agentBaseUrl
            val failedIds = mutableListOf<String>()
            val succeededIds = mutableListOf<String>()

            for (session in sessions) {
                try {
                    val response = chatRepository.resetServerSession(baseUrl, session.sessionId)
                    if (response.session_id == session.sessionId && response.status.equals("cleared", ignoreCase = true)) {
                        succeededIds.add(session.sessionId)
                        chatRepository.clearMessagesForSession(session.sessionId)
                    } else {
                        failedIds.add(session.sessionId)
                    }
                } catch (_: Exception) {
                    failedIds.add(session.sessionId)
                }
            }

            if (failedIds.isEmpty()) {
                _uiState.update { state ->
                    state.copy(
                        messages = emptyList(),
                        isClearingChat = false,
                        clearChatError = null,
                        failedClearSessionIds = emptyList(),
                        agentChatRetryKey = null,
                        agentChatRetryBaseUrl = null
                    )
                }
            } else {
                val total = sessions.size
                val successCount = succeededIds.size
                val errorMsg = "Cleared $successCount of $total chats on server. Server history may remain for ${failedIds.size} session(s)."
                _uiState.update { state ->
                    state.copy(
                        messages = if (state.sessionId in succeededIds) emptyList() else state.messages,
                        isClearingChat = false,
                        clearChatError = errorMsg,
                        failedClearSessionIds = failedIds
                    )
                }
            }
        }
    }

    fun retryClearFailedChats() {
        val failed = _uiState.value.failedClearSessionIds
        if (failed.isEmpty()) {
            retryClearCurrentConversation()
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isClearingChat = true, clearChatError = null) }
            val baseUrl = _uiState.value.agentBaseUrl
            val stillFailed = mutableListOf<String>()
            val newlySucceeded = mutableListOf<String>()

            for (id in failed) {
                try {
                    val response = chatRepository.resetServerSession(baseUrl, id)
                    if (response.session_id == id && response.status.equals("cleared", ignoreCase = true)) {
                        newlySucceeded.add(id)
                        chatRepository.clearMessagesForSession(id)
                    } else {
                        stillFailed.add(id)
                    }
                } catch (_: Exception) {
                    stillFailed.add(id)
                }
            }

            if (stillFailed.isEmpty()) {
                _uiState.update { state ->
                    state.copy(
                        messages = if (state.sessionId in newlySucceeded) emptyList() else state.messages,
                        isClearingChat = false,
                        clearChatError = null,
                        failedClearSessionIds = emptyList()
                    )
                }
            } else {
                val errorMsg = "Retry: ${newlySucceeded.size} cleared, ${stillFailed.size} still failed on server. Server history may remain."
                _uiState.update { state ->
                    state.copy(
                        messages = if (state.sessionId in newlySucceeded) emptyList() else state.messages,
                        isClearingChat = false,
                        clearChatError = errorMsg,
                        failedClearSessionIds = stillFailed
                    )
                }
            }
        }
    }

    private fun sessionTitleFromPrompt(prompt: String): String {
        val words = prompt.trim()
            .replace(Regex("[^\\p{L}\\p{N} _-]"), " ")
            .split(Regex("\\s+"))
            .filter(String::isNotBlank)
            .take(5)
            .toMutableList()
        while (words.size < 4) words += if (words.size % 2 == 0) "chat" else "session"
        return words.joinToString(" ").replaceFirstChar { it.uppercase() }
    }

    fun toggleThinkingExpanded(messageId: String) {
        _uiState.update { currentState ->
            val updated = currentState.messages.map { msg ->
                if (msg.id == messageId) {
                    msg.copy(isThinkingExpanded = !msg.isThinkingExpanded)
                } else {
                    msg
                }
            }
            currentState.copy(messages = updated)
        }
    }

    fun toggleActionExpanded(messageId: String) {
        _uiState.update { currentState ->
            val updated = currentState.messages.map { msg ->
                if (msg.id == messageId) {
                    msg.copy(isActionExpanded = !msg.isActionExpanded)
                } else {
                    msg
                }
            }
            currentState.copy(messages = updated)
        }
    }

    fun openSettings() {
        _uiState.update { it.copy(showSettingsDialog = true) }
    }

    fun closeSettings() {
        _uiState.update { it.copy(showSettingsDialog = false) }
    }

    fun saveSettings(
        mode: ConnectionMode,
        newAgentUrl: String,
        newPureChatUrl: String,
        newSystemPrompt: String,
        newModel: String,
        newTerminalTheme: String
    ) {
        val previousTargetUrl = _uiState.value.activeBaseUrl

        val cleanedAgentUrl = newAgentUrl.trim().removeSuffix("/")
        val cleanedPureChatUrl = newPureChatUrl.trim().removeSuffix("/")

        _uiState.update {
            it.copy(
                connectionMode = mode,
                agentBaseUrl = cleanedAgentUrl,
                pureChatBaseUrl = cleanedPureChatUrl,
                systemPrompt = newSystemPrompt,
                selectedModel = newModel,
                terminalTheme = newTerminalTheme,
                showSettingsDialog = false
            )
        }

        viewModelScope.launch {
            preferencesRepository.saveConnectionMode(mode)
            preferencesRepository.saveAgentBaseUrl(cleanedAgentUrl)
            preferencesRepository.savePureChatBaseUrl(cleanedPureChatUrl)
            preferencesRepository.saveSystemPrompt(newSystemPrompt)
            preferencesRepository.saveSelectedModel(newModel)
            preferencesRepository.saveTerminalTheme(newTerminalTheme)

            if (_uiState.value.activeBaseUrl != previousTargetUrl) {
                fetchModels()
                if (mode == ConnectionMode.AGENT) fetchBridgeHelp()
            }
        }
    }

    fun setForceLegacyChat(enabled: Boolean) {
        _uiState.update { it.copy(forceLegacyChat = enabled) }
        viewModelScope.launch { preferencesRepository.saveForceLegacyChat(enabled) }
    }

    fun setDelegateLongWork(enabled: Boolean) {
        _uiState.update { it.copy(delegateLongWork = enabled) }
        viewModelScope.launch { preferencesRepository.saveDelegateLongWork(enabled) }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
