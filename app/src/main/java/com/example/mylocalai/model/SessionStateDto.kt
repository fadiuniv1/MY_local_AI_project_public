package com.example.mylocalai.model

import com.google.gson.annotations.SerializedName

data class SessionStateDto(
    val goal: String = "",
    val workspace: String = "",
    val files: List<String> = emptyList(),
    val decisions: List<String> = emptyList(),
    val constraints: List<String> = emptyList(),
    @SerializedName(value = "last_success", alternate = ["lastSuccess"])
    val lastSuccess: LastSuccessDto? = null,
    @SerializedName(value = "last_failure", alternate = ["lastFailure"])
    val lastFailure: String = "",
)

data class LastSuccessDto(
    val goal: String = "",
    val workspace: String = "",
    val files: List<String> = emptyList(),
    val answer: String = "",
    val prompt: String = "",
    val at: String = "",
)
