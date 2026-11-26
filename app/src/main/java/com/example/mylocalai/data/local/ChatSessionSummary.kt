package com.example.mylocalai.data.local

import androidx.room.ColumnInfo

data class ChatSessionSummary(
    @ColumnInfo(name = "sessionId") val sessionId: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "lastUpdated") val lastUpdated: Long,
    @ColumnInfo(name = "preview") val preview: String,
    @ColumnInfo(name = "messageCount") val messageCount: Int,
)
