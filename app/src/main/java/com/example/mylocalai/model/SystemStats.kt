package com.example.mylocalai.model

data class SystemStats(
    val cpuUsage: String = "N/A",
    val uptime: String = "N/A",
    val ramUsed: String = "N/A",
    val ramTotal: String = "N/A",
    val ramPercent: String = "N/A",
    val diskUsed: String = "N/A",
    val diskTotal: String = "N/A",
    val diskPercent: String = "N/A"
)

data class FilePreviewState(
    val filePath: String = "",
    val fileContent: String = "",
    val mimeType: String = "text/plain",
    val imageBytes: ByteArray? = null,
    val byteCount: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val downloadProgress: Float? = null,
    val canRetry: Boolean = false
)
