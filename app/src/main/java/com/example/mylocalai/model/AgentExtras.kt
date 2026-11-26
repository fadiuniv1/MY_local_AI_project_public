package com.example.mylocalai.model

data class PlanRequest(
    val prompt: String,
    val session_id: String = "",
    val tool_mode: String = "direct",
)

data class PlanResponse(
    val plan: String = "",
    val workspace: String = "",
    val files: List<String> = emptyList(),
    val requires_approval: Boolean = false,
)

data class ReviewRequest(
    val session_id: String,
    val files: List<String> = emptyList(),
)

data class ReviewResponse(
    val findings: String = "",
    val files_reviewed: List<String> = emptyList(),
)

data class FileWriteRequest(
    val path: String,
    val content: String,
    val as_root: Boolean = false,
    val confirm: Boolean = false,
)
