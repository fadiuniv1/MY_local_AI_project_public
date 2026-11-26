package com.example.mylocalai.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_sessions ORDER BY lastUpdated DESC, sessionId DESC")
    fun getAllSessionsFlow(): Flow<List<ChatSessionEntity>>

    @Query("""
        SELECT s.sessionId AS sessionId,
               s.title AS title,
               s.lastUpdated AS lastUpdated,
               COALESCE((SELECT substr(m.content, 1, 120) FROM chat_messages m
                         WHERE m.sessionId = s.sessionId AND trim(m.content) != ''
                         ORDER BY m.createdAt DESC LIMIT 1), '') AS preview,
               (SELECT COUNT(*) FROM chat_messages m WHERE m.sessionId = s.sessionId) AS messageCount
        FROM chat_sessions s
        WHERE :query = ''
           OR s.title LIKE '%' || :query || '%'
           OR EXISTS (SELECT 1 FROM chat_messages m WHERE m.sessionId = s.sessionId AND m.content LIKE '%' || :query || '%')
        ORDER BY s.lastUpdated DESC, s.sessionId DESC
    """)
    fun searchSessionsFlow(query: String): Flow<List<ChatSessionSummary>>

    @Query("SELECT * FROM chat_sessions ORDER BY lastUpdated DESC, sessionId DESC")
    suspend fun getAllSessions(): List<ChatSessionEntity>

    @Query("SELECT * FROM chat_sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getSession(sessionId: String): ChatSessionEntity?

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC, id ASC")
    fun getMessagesFlow(sessionId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT MAX(createdAt) FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun getLatestMessageTimestamp(sessionId: String): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSession(session: ChatSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: ChatMessageEntity)

    @Query("UPDATE chat_sessions SET title = :title, lastUpdated = MAX(lastUpdated + 1, :updatedAt) WHERE sessionId = :sessionId")
    suspend fun updateSessionTitleAndTimestamp(sessionId: String, title: String, updatedAt: Long)

    @Query("UPDATE chat_sessions SET lastUpdated = MAX(lastUpdated + 1, :updatedAt) WHERE sessionId = :sessionId")
    suspend fun touchSession(sessionId: String, updatedAt: Long)

    @Query("DELETE FROM chat_messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: String)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun clearMessagesForSession(sessionId: String)

    @Query("DELETE FROM chat_messages WHERE sessionId IN (:sessionIds)")
    suspend fun clearMessagesForSessions(sessionIds: List<String>)

    @Query("DELETE FROM chat_sessions WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM chat_sessions")
    suspend fun deleteAllSessions()

}
