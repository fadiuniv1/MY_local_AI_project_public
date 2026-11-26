package com.example.mylocalai.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ChatSessionEntity::class, ChatMessageEntity::class], version = 3, exportSchema = false)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN toolResultsJson TEXT NOT NULL DEFAULT '[]'")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN jobStatus TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN elapsedSeconds INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN isJobFailure INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN latestEvent TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN delegatedTaskId TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN delegatedSummary TEXT DEFAULT NULL")
            }
        }

        @Volatile private var instance: ChatDatabase? = null

        fun getInstance(context: Context): ChatDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                ChatDatabase::class.java,
                "chat_history.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }
    }
}
