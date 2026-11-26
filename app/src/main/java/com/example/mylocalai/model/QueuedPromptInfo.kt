package com.example.mylocalai.model

/** One prompt waiting in the Android send queue, for the queue panel UI. */
data class QueuedPromptInfo(
    val assistantId: String = "",
    val text: String = "",
    val position: Int = 0,
    val imageCount: Int = 0,
)
