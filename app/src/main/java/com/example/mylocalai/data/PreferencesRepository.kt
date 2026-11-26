package com.example.mylocalai.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.mylocalai.model.ConnectionMode
import com.example.mylocalai.model.BridgeAuditEntry
import com.example.mylocalai.model.PendingSoftwareTaskCreate
import com.example.mylocalai.model.SoftwareTaskRequest
import com.example.mylocalai.model.TrackedSoftwareTask
import com.example.mylocalai.model.SoftwareTaskEvent
import com.example.mylocalai.model.PendingAgentChatRequest
import com.example.mylocalai.model.QueuedPromptData
import com.example.mylocalai.model.PersistedChatJob
import com.example.mylocalai.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import com.google.gson.Gson
import java.util.UUID

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ollama_preferences")

class PreferencesRepository(private val context: Context) {

    companion object {
        val KEY_CONNECTION_MODE = stringPreferencesKey("connection_mode")
        val KEY_AGENT_BASE_URL = stringPreferencesKey("agent_base_url")
        val KEY_PURE_CHAT_BASE_URL = stringPreferencesKey("pure_chat_base_url")
        val KEY_SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        val KEY_SELECTED_MODEL = stringPreferencesKey("selected_model")
        val KEY_TERMINAL_THEME = stringPreferencesKey("terminal_theme")
        val KEY_ACTIVE_SESSION_ID = stringPreferencesKey("active_session_id")
        private val KEY_BRIDGE_AUDIT = stringPreferencesKey("bridge_audit_history")
        private val KEY_NOTES_AFTER_ID = longPreferencesKey("agent_notes_after_id")
        private val KEY_SOFTWARE_TASKS = stringPreferencesKey("tracked_software_tasks")
        private val KEY_PENDING_SOFTWARE_TASK = stringPreferencesKey("pending_software_task_create")
        private val KEY_PENDING_AGENT_CHAT = stringPreferencesKey("pending_agent_chat_request")
        private val KEY_ACTIVE_CHAT_JOB = stringPreferencesKey("active_chat_job")
        private val KEY_QUEUED_PROMPTS = stringPreferencesKey("queued_chat_prompts")
        private val KEY_NOTIFY_REPLIES = booleanPreferencesKey("notify_replies")
        private val KEY_ALLOWED_TOOLS = stringPreferencesKey("allowed_tools")
        private val KEY_NOTIFY_DOWNLOADS = booleanPreferencesKey("notify_downloads")
        private val KEY_NOTIFY_TASKS = booleanPreferencesKey("notify_tasks")
        private val KEY_FORCE_LEGACY_CHAT = booleanPreferencesKey("force_legacy_chat")
        private val KEY_DELEGATE_LONG_WORK = booleanPreferencesKey("delegate_long_work")

        val DEFAULT_AGENT_BASE_URL = BuildConfig.REMOTE_API_BASE_URL.trimEnd('/')
        val DEFAULT_PURE_CHAT_BASE_URL = BuildConfig.REMOTE_API_BASE_URL.trimEnd('/').replace(Regex(":\\d+$"), ":11434")
        const val DEFAULT_SYSTEM_PROMPT = "You are a concise, expert coding & creation assistant."
        const val DEFAULT_SELECTED_MODEL = "qwen2.5-coder:1.5b"
    }

    val connectionModeFlow: Flow<ConnectionMode> = context.dataStore.data.map { preferences ->
        val modeStr = preferences[KEY_CONNECTION_MODE] ?: ConnectionMode.AGENT.name
        try {
            ConnectionMode.valueOf(modeStr)
        } catch (_: Exception) {
            ConnectionMode.AGENT
        }
    }

    val agentBaseUrlFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_AGENT_BASE_URL] ?: DEFAULT_AGENT_BASE_URL
    }

    val pureChatBaseUrlFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_PURE_CHAT_BASE_URL] ?: DEFAULT_PURE_CHAT_BASE_URL
    }

    val systemPromptFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_SYSTEM_PROMPT] ?: DEFAULT_SYSTEM_PROMPT
    }

    val selectedModelFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_SELECTED_MODEL] ?: DEFAULT_SELECTED_MODEL
    }

    val terminalThemeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_TERMINAL_THEME] ?: "Dracula"
    }

    val activeSessionIdFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_ACTIVE_SESSION_ID]
    }

    val bridgeAuditHistoryFlow: Flow<List<BridgeAuditEntry>> = context.dataStore.data.map { preferences ->
        decodeAuditHistory(preferences[KEY_BRIDGE_AUDIT].orEmpty())
    }

    suspend fun recordBridgeAudit(action: String, detail: String, succeeded: Boolean) {
        context.dataStore.edit { preferences ->
            val current = decodeAuditHistory(preferences[KEY_BRIDGE_AUDIT].orEmpty())
            val entry = BridgeAuditEntry(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                action = action.take(160),
                detail = detail.take(1600),
                succeeded = succeeded,
            )
            preferences[KEY_BRIDGE_AUDIT] = JSONArray().apply {
                (listOf(entry) + current).take(100).forEach { item ->
                    put(JSONObject().apply {
                        put("id", item.id)
                        put("timestamp", item.timestamp)
                        put("action", item.action)
                        put("detail", item.detail)
                        put("succeeded", item.succeeded)
                    })
                }
            }.toString()
        }
    }

    suspend fun clearBridgeAuditHistory() {
        context.dataStore.edit { it[KEY_BRIDGE_AUDIT] = "[]" }
    }

    private fun decodeAuditHistory(raw: String): List<BridgeAuditEntry> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    BridgeAuditEntry(
                        id = item.optString("id", index.toString()),
                        timestamp = item.optLong("timestamp"),
                        action = item.optString("action", "Bridge action"),
                        detail = item.optString("detail", ""),
                        succeeded = item.optBoolean("succeeded"),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    suspend fun saveConnectionMode(mode: ConnectionMode) {
        context.dataStore.edit { preferences ->
            preferences[KEY_CONNECTION_MODE] = mode.name
        }
    }

    suspend fun saveAgentBaseUrl(baseUrl: String) {
        val cleanedUrl = baseUrl.trim().removeSuffix("/")
        context.dataStore.edit { preferences ->
            preferences[KEY_AGENT_BASE_URL] = cleanedUrl
        }
    }

    suspend fun savePureChatBaseUrl(baseUrl: String) {
        val cleanedUrl = baseUrl.trim().removeSuffix("/")
        context.dataStore.edit { preferences ->
            preferences[KEY_PURE_CHAT_BASE_URL] = cleanedUrl
        }
    }

    suspend fun saveSystemPrompt(prompt: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SYSTEM_PROMPT] = prompt.trim()
        }
    }

    suspend fun saveSelectedModel(model: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SELECTED_MODEL] = model
        }
    }

    suspend fun saveTerminalTheme(theme: String) {
        context.dataStore.edit { preferences -> preferences[KEY_TERMINAL_THEME] = theme }
    }

    suspend fun saveActiveSessionId(sessionId: String) {
        context.dataStore.edit { preferences -> preferences[KEY_ACTIVE_SESSION_ID] = sessionId }
    }

    suspend fun getNotesAfterId(): Long = context.dataStore.data.map { it[KEY_NOTES_AFTER_ID] ?: 0L }.first()

    suspend fun saveNotesAfterId(id: Long) {
        context.dataStore.edit { it[KEY_NOTES_AFTER_ID] = id.coerceAtLeast(0L) }
    }

    suspend fun getTrackedSoftwareTasks(): List<TrackedSoftwareTask> = runCatching {
        val raw = context.dataStore.data.map { it[KEY_SOFTWARE_TASKS].orEmpty() }.first()
        Gson().fromJson(raw, Array<TrackedSoftwareTask>::class.java)?.toList().orEmpty()
    }.getOrDefault(emptyList())

    suspend fun saveTrackedSoftwareTasks(tasks: List<TrackedSoftwareTask>) {
        context.dataStore.edit { it[KEY_SOFTWARE_TASKS] = Gson().toJson(tasks.associateBy { task -> task.taskId }.values.toList().takeLast(100)) }
    }

    suspend fun savePendingSoftwareTaskCreate(pending: PendingSoftwareTaskCreate) {
        context.dataStore.edit { it[KEY_PENDING_SOFTWARE_TASK] = Gson().toJson(pending) }
    }

    suspend fun getPendingSoftwareTaskCreate(): PendingSoftwareTaskCreate? {
        val raw = context.dataStore.data.map { it[KEY_PENDING_SOFTWARE_TASK] }.first() ?: return null
        return runCatching { Gson().fromJson(raw, PendingSoftwareTaskCreate::class.java) }.getOrNull()
    }

    suspend fun clearPendingSoftwareTaskCreate() {
        context.dataStore.edit { it.remove(KEY_PENDING_SOFTWARE_TASK) }
    }

    suspend fun getPendingAgentChatRequest(): PendingAgentChatRequest? {
        return getPendingAgentChatRequests().lastOrNull()
    }

    suspend fun getPendingAgentChatRequests(): List<PendingAgentChatRequest> {
        val raw = context.dataStore.data.map { it[KEY_PENDING_AGENT_CHAT] }.first() ?: return emptyList()
        return runCatching { Gson().fromJson(raw, Array<PendingAgentChatRequest>::class.java)?.toList() }
            .getOrNull()
            ?: runCatching { listOfNotNull(Gson().fromJson(raw, PendingAgentChatRequest::class.java)) }.getOrDefault(emptyList())
    }

    suspend fun savePendingAgentChatRequest(request: PendingAgentChatRequest) {
        val current = getPendingAgentChatRequests()
        context.dataStore.edit {
            it[KEY_PENDING_AGENT_CHAT] = Gson().toJson((current.filterNot { pending -> pending.idempotencyKey == request.idempotencyKey } + request).takeLast(100))
        }
    }

    suspend fun clearPendingAgentChatRequest(idempotencyKey: String) {
        context.dataStore.edit { preferences ->
            val raw = preferences[KEY_PENDING_AGENT_CHAT] ?: return@edit
            val pending = runCatching { Gson().fromJson(raw, Array<PendingAgentChatRequest>::class.java)?.toList() }
                .getOrNull()
                ?: runCatching { listOfNotNull(Gson().fromJson(raw, PendingAgentChatRequest::class.java)) }.getOrDefault(emptyList())
            val remaining = pending.filterNot { it.idempotencyKey == idempotencyKey }
            if (remaining.isEmpty()) preferences.remove(KEY_PENDING_AGENT_CHAT)
            else preferences[KEY_PENDING_AGENT_CHAT] = Gson().toJson(remaining)
        }
    }

    suspend fun getQueuedPrompts(): List<QueuedPromptData> {
        val raw = context.dataStore.data.map { it[KEY_QUEUED_PROMPTS] }.first() ?: return emptyList()
        return runCatching {
            Gson().fromJson(raw, Array<QueuedPromptData>::class.java)?.toList().orEmpty()
        }.getOrDefault(emptyList())
    }

    suspend fun saveQueuedPrompts(items: List<QueuedPromptData>) {
        context.dataStore.edit { it[KEY_QUEUED_PROMPTS] = Gson().toJson(items.takeLast(50)) }
    }

    suspend fun clearQueuedPrompts() {
        context.dataStore.edit { it.remove(KEY_QUEUED_PROMPTS) }
    }

    val allowedToolsFlow: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_ALLOWED_TOOLS]
        if (raw.isNullOrBlank()) emptySet()
        else runCatching {
            Gson().fromJson(raw, Array<String>::class.java)?.toSet()
        }.getOrDefault(emptySet()) ?: emptySet()
    }

    suspend fun saveAllowedTools(tools: Set<String>) {
        context.dataStore.edit { it[KEY_ALLOWED_TOOLS] = Gson().toJson(tools.toList().sorted()) }
    }

    val notifyRepliesFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_NOTIFY_REPLIES] ?: true }
    val notifyDownloadsFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_NOTIFY_DOWNLOADS] ?: true }
    val notifyTasksFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_NOTIFY_TASKS] ?: true }

    suspend fun saveNotificationPrefs(replies: Boolean, downloads: Boolean, tasks: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_NOTIFY_REPLIES] = replies
            prefs[KEY_NOTIFY_DOWNLOADS] = downloads
            prefs[KEY_NOTIFY_TASKS] = tasks
        }
    }

    val forceLegacyChatFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_FORCE_LEGACY_CHAT] ?: false }
    val delegateLongWorkFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_DELEGATE_LONG_WORK] ?: false }

    suspend fun saveForceLegacyChat(enabled: Boolean) {
        context.dataStore.edit { it[KEY_FORCE_LEGACY_CHAT] = enabled }
    }

    suspend fun saveDelegateLongWork(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DELEGATE_LONG_WORK] = enabled }
    }

    suspend fun getActiveChatJob(): PersistedChatJob? {
        val raw = context.dataStore.data.map { it[KEY_ACTIVE_CHAT_JOB] }.first() ?: return null
        return runCatching { Gson().fromJson(raw, PersistedChatJob::class.java) }
            .getOrNull()
            ?.takeIf { it.requestId.isNotBlank() }
    }

    suspend fun saveActiveChatJob(job: PersistedChatJob) {
        context.dataStore.edit { it[KEY_ACTIVE_CHAT_JOB] = Gson().toJson(job) }
    }

    suspend fun clearActiveChatJob() {
        context.dataStore.edit { it.remove(KEY_ACTIVE_CHAT_JOB) }
    }

    suspend fun getSoftwareTaskEvents(taskId: String): List<SoftwareTaskEvent> = runCatching {
        val key = stringPreferencesKey("software_task_events_${taskId.take(120)}")
        val raw = context.dataStore.data.map { it[key].orEmpty() }.first()
        Gson().fromJson(raw, Array<SoftwareTaskEvent>::class.java)?.toList().orEmpty()
    }.getOrDefault(emptyList())

    suspend fun saveSoftwareTaskEvents(taskId: String, events: List<SoftwareTaskEvent>) {
        val key = stringPreferencesKey("software_task_events_${taskId.take(120)}")
        context.dataStore.edit { it[key] = Gson().toJson(events.distinctBy(SoftwareTaskEvent::id).takeLast(300)) }
    }
}
