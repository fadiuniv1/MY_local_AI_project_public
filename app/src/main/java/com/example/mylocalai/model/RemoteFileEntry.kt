package com.example.mylocalai.model

data class RemoteFileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val isSymlink: Boolean = false,
    val size: Long? = null,
    val modified: String? = null,
)

data class RemoteFileListing(
    val entries: List<RemoteFileEntry> = emptyList(),
    val truncated: Boolean = false,
    val elevated: Boolean = false,
)

data class FileTransferRequest(
    val source: String,
    val destination: String,
    val operation: String,
    val as_root: Boolean = false,
    val confirm: Boolean = true,
)

data class RemoteFileDownload(
    val content: String = "",
    val mimeType: String,
    val imageBytes: ByteArray? = null,
    val byteCount: Int,
)
