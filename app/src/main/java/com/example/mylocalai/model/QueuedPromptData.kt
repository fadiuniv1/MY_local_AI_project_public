package com.example.mylocalai.model

/** Persisted representation of a chat prompt waiting in the send queue. */
data class QueuedPromptData(
    val text: String = "",
    val images: List<String> = emptyList(),
    val userMsgId: String = "",
    val assistantId: String = "",
    val sessionId: String = "",
)
