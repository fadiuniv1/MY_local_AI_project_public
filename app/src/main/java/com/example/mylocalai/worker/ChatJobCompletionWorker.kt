package com.example.mylocalai.worker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.mylocalai.data.ChatRepository
import com.example.mylocalai.data.PreferencesRepository
import com.example.mylocalai.notification.AgentNotifications
import com.example.mylocalai.notification.AppVisibility
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

private const val KEY_BASE_URL = "chat_base_url"
private const val KEY_REQUEST_ID = "chat_request_id"
private const val KEY_SESSION_ID = "chat_session_id"
private const val KEY_PROMPT = "chat_prompt"
private const val WORK_NAME_PREFIX = "agent-chat-monitor-"
private const val POLL_SEGMENT_MS = 7 * 60 * 1000L
private val TERMINAL = setOf("completed", "delegated", "failed", "cancelled", "interrupted")

object ChatJobCompletionMonitor {
    fun enqueue(context: Context, baseUrl: String, requestId: String, sessionId: String, prompt: String) {
        val input = workDataOf(
            KEY_BASE_URL to baseUrl,
            KEY_REQUEST_ID to requestId,
            KEY_SESSION_ID to sessionId,
            KEY_PROMPT to prompt.take(500),
        )
        runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_PREFIX + requestId,
                ExistingWorkPolicy.KEEP,
                buildRequest(input),
            )
        }.onFailure { Log.w("ChatJobMonitor", "Could not schedule chat job monitor", it) }
    }

    internal fun continueMonitoring(context: Context, input: Data) {
        runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_PREFIX + (input.getString(KEY_REQUEST_ID) ?: return),
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                buildRequest(input),
            )
        }.onFailure { Log.w("ChatJobMonitor", "Could not continue chat job monitor", it) }
    }

    private fun buildRequest(input: Data) = OneTimeWorkRequestBuilder<ChatJobCompletionWorker>()
        .setInputData(input)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .build()
}

/** Watches a chat job while the app is backgrounded/killed and posts completion once. */
class ChatJobCompletionWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val baseUrl = inputData.getString(KEY_BASE_URL) ?: return Result.failure()
        val requestId = inputData.getString(KEY_REQUEST_ID) ?: return Result.failure()
        val sessionId = inputData.getString(KEY_SESSION_ID).orEmpty()
        val prompt = inputData.getString(KEY_PROMPT).orEmpty()
        val repository = ChatRepository(applicationContext)
        val prefs = PreferencesRepository(applicationContext)
        val startedAt = System.currentTimeMillis()
        var delayMs = 2_000L

        while (System.currentTimeMillis() - startedAt < POLL_SEGMENT_MS) {
            try {
                val status = repository.getAgentChatJob(baseUrl, requestId)
                if (status.status.lowercase() in TERMINAL) {
                    prefs.clearActiveChatJob()
                    if (!AppVisibility.isForeground && prefs.notifyRepliesFlow.first()) {
                        val answer = status.response?.message?.content?.takeIf(String::isNotBlank)
                            ?: status.response?.assistant_text?.takeIf(String::isNotBlank)
                            ?: "Job ${status.status}"
                        AgentNotifications.notifyReply(
                            applicationContext, sessionId, prompt, answer,
                            requestId = requestId, baseUrl = baseUrl,
                        )
                    }
                    return Result.success()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep watching through brief reconnects.
            }
            delay(delayMs)
            delayMs = (delayMs * 2).coerceAtMost(15_000L)
        }

        ChatJobCompletionMonitor.continueMonitoring(applicationContext, inputData)
        return Result.success()
    }
}
