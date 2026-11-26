package com.example.mylocalai.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.mylocalai.data.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Handles the Stop action on a chat reply notification without opening the UI. */
class ChatJobActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val jobId = intent.getStringExtra("chat_job_id")?.takeIf(String::isNotBlank) ?: return
        val baseUrl = intent.getStringExtra("chat_job_base_url")?.takeIf(String::isNotBlank) ?: return
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ChatRepository(context.applicationContext).cancelAgentChatJob(baseUrl, jobId)
            } catch (_: Exception) {
                // Best effort; the server may have already finished the job.
            } finally {
                result.finish()
            }
        }
    }
}
