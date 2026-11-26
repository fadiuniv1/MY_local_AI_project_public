package com.example.mylocalai.model

import java.util.UUID
import com.example.mylocalai.model.ToolResultDto

enum class ConnectionMode {
    AGENT,
    PURE_CHAT
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String, // "user", "assistant", "system"
    val content: String = "",
    val thinking: String = "",
    val actionSummary: String = "",
    val toolResults: List<ToolResultDto> = emptyList(),
    val isThinkingExpanded: Boolean = false,
    val isActionExpanded: Boolean = true,
    val isStreaming: Boolean = false,
    val jobStatus: String? = null,
    val queuePosition: Int? = null,
    val elapsedSeconds: Long? = null,
    val isJobFailure: Boolean = false,
    val latestEvent: String? = null,
    val delegatedTaskId: String? = null,
    val delegatedSummary: String? = null
)

data class StreamChunk(
    val content: String = "",
    val thinking: String = "",
    val actionSummary: String = "",
    val isDone: Boolean = false
)
