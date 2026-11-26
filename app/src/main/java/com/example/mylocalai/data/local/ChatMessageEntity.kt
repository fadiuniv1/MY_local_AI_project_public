package com.example.mylocalai.data.local

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId"), Index(value = ["sessionId", "createdAt"])]
)
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: String,
    val content: String,
    val thinking: String,
    val actionSummary: String,
    @ColumnInfo(defaultValue = "'[]'") val toolResultsJson: String = "[]",
    val createdAt: Long,
    @ColumnInfo(defaultValue = "NULL") val jobStatus: String? = null,
    @ColumnInfo(defaultValue = "NULL") val elapsedSeconds: Long? = null,
    @ColumnInfo(defaultValue = "0") val isJobFailure: Boolean = false,
    @ColumnInfo(defaultValue = "NULL") val latestEvent: String? = null,
    @ColumnInfo(defaultValue = "NULL") val delegatedTaskId: String? = null,
    @ColumnInfo(defaultValue = "NULL") val delegatedSummary: String? = null,
)
