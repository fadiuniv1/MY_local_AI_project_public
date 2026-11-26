package com.example.mylocalai.data.local

import android.content.Context
import com.example.mylocalai.model.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import com.example.mylocalai.model.ToolResultDto
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class LocalChatRepository(context: Context) {
    private val dao = ChatDatabase.getInstance(context).chatDao()
    private val gson = Gson()
    private val toolResultsType = object : TypeToken<List<ToolResultDto>>() {}.type

    fun getAllSessionsFlow(): Flow<List<ChatSessionEntity>> = dao.getAllSessionsFlow()
    fun searchSessionsFlow(query: String): Flow<List<ChatSessionSummary>> = dao.searchSessionsFlow(query.trim())
    suspend fun getAllSessions(): List<ChatSessionEntity> = dao.getAllSessions()
    suspend fun getSession(sessionId: String): ChatSessionEntity? = dao.getSession(sessionId)

    fun getMessagesFlow(sessionId: String): Flow<List<ChatMessage>> = dao.getMessagesFlow(sessionId).map { rows ->
        rows.map { row ->
            ChatMessage(
                id = row.id,
                role = row.role,
                content = row.content,
                thinking = row.thinking,
                actionSummary = row.actionSummary,
                toolResults = runCatching { gson.fromJson<List<ToolResultDto>>(row.toolResultsJson, toolResultsType) }.getOrNull().orEmpty(),
                isStreaming = false,
                jobStatus = row.jobStatus,
                elapsedSeconds = row.elapsedSeconds,
                isJobFailure = row.isJobFailure,
                latestEvent = row.latestEvent,
                delegatedTaskId = row.delegatedTaskId,
                delegatedSummary = row.delegatedSummary
            )
        }
    }

    suspend fun createSession(sessionId: String, title: String = "New chat") {
        dao.insertSession(ChatSessionEntity(sessionId, title, System.currentTimeMillis()))
    }

    suspend fun renameSession(sessionId: String, title: String) {
        dao.updateSessionTitleAndTimestamp(sessionId, title, System.currentTimeMillis())
    }

    suspend fun saveMessage(sessionId: String, message: ChatMessage) {
        val createdAt = maxOf(System.currentTimeMillis(), (dao.getLatestMessageTimestamp(sessionId) ?: 0L) + 1L)
        dao.upsertMessage(
            ChatMessageEntity(
                id = message.id.ifBlank { UUID.randomUUID().toString() },
                sessionId = sessionId,
                role = message.role,
                content = message.content,
                thinking = message.thinking,
                actionSummary = message.actionSummary,
                toolResultsJson = gson.toJson(message.toolResults),
                createdAt = createdAt,
                jobStatus = message.jobStatus,
                elapsedSeconds = message.elapsedSeconds,
                isJobFailure = message.isJobFailure,
                latestEvent = message.latestEvent,
                delegatedTaskId = message.delegatedTaskId,
                delegatedSummary = message.delegatedSummary
            )
        )
        dao.touchSession(sessionId, System.currentTimeMillis())
    }

    suspend fun deleteMessage(messageId: String) = dao.deleteMessage(messageId)

    suspend fun clearMessagesForSession(sessionId: String) {
        dao.clearMessagesForSession(sessionId)
        dao.touchSession(sessionId, System.currentTimeMillis())
    }

    suspend fun clearMessagesForSessions(sessionIds: List<String>) {
        dao.clearMessagesForSessions(sessionIds)
        val now = System.currentTimeMillis()
        sessionIds.forEach { dao.touchSession(it, now) }
    }

    suspend fun deleteSession(sessionId: String) = dao.deleteSession(sessionId)
    suspend fun deleteAllSessions() = dao.deleteAllSessions()
}
