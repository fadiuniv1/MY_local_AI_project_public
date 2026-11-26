package com.example.mylocalai.model

/** Server-backed task memory for one chat tab. */
data class SessionTaskInfo(
    val sessionId: String = "",
    val goal: String = "",
    val workspace: String = "",
    val files: List<String> = emptyList(),
    val lastSuccessAt: String = "",
    val status: String = "",
)
