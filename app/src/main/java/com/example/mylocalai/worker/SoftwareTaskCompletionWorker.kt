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
import com.example.mylocalai.data.remote.RetrofitClient
import com.example.mylocalai.data.remote.SoftwareTaskParser
import com.example.mylocalai.data.PreferencesRepository
import com.example.mylocalai.notification.AgentNotifications
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import retrofit2.HttpException

private const val KEY_TASK_BASE_URL = "software_task_base_url"
private const val KEY_TASK_ID = "software_task_id"
private const val KEY_TASK_STATUS_URL = "software_task_status_url"
private const val TASK_MONITOR_SEGMENT_MS = 7 * 60 * 1000L
private const val TASK_MONITOR_PREFIX = "software-task-monitor-"
private val MONITORED_TASK_STATUSES = setOf("queued", "running")
private val HUMAN_ACTION_STATUSES = setOf("paused", "needs_input")
private val FINAL_TASK_STATUSES = setOf("completed", "failed", "cancelled")

object SoftwareTaskMonitor {
    fun enqueue(context: Context, baseUrl: String, taskId: String, statusUrl: String? = null) {
        val input = workDataOf(KEY_TASK_BASE_URL to baseUrl, KEY_TASK_ID to taskId, KEY_TASK_STATUS_URL to statusUrl.orEmpty())
        val request = OneTimeWorkRequestBuilder<SoftwareTaskCompletionWorker>()
            .setInputData(input)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork(TASK_MONITOR_PREFIX + taskId, ExistingWorkPolicy.KEEP, request)
        }.onFailure { Log.w("SoftwareTaskMonitor", "Could not schedule monitoring for $taskId", it) }
    }

    internal fun continueMonitoring(context: Context, input: Data) {
        val taskId = input.getString(KEY_TASK_ID) ?: return
        val request = OneTimeWorkRequestBuilder<SoftwareTaskCompletionWorker>()
            .setInputData(input)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork(TASK_MONITOR_PREFIX + taskId, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }.onFailure { Log.w("SoftwareTaskMonitor", "Could not continue monitoring $taskId", it) }
    }
}

class SoftwareTaskCompletionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val baseUrl = inputData.getString(KEY_TASK_BASE_URL) ?: return Result.failure()
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.failure()
        val statusUrl = inputData.getString(KEY_TASK_STATUS_URL)?.takeIf(String::isNotBlank)
        val prefs = PreferencesRepository(applicationContext)
        val api = runCatching { RetrofitClient.createApiService(baseUrl) }.getOrElse { return Result.retry() }
        val startedAt = System.currentTimeMillis()
        var waitMs = 2_000L
        var lastProgress = ""
        while (System.currentTimeMillis() - startedAt < TASK_MONITOR_SEGMENT_MS) {
            try {
                val response = if (statusUrl.isNullOrBlank()) api.getSoftwareTask(taskId) else api.getSoftwareTaskAt(statusUrl)
                val task = response.use { SoftwareTaskParser.parseTask(it.string(), taskId) }
                val status = task.status.lowercase()
                val signature = "${task.status}|${task.phase}|${task.stepsDone}|${task.stepsTotal}"
                if (signature != lastProgress) {
                    if (prefs.notifyTasksFlow.first()) AgentNotifications.notifySoftwareTaskProgress(
                        applicationContext, task.taskId, task.objective, task.status, task.phase, task.stepsDone, task.stepsTotal
                    )
                    lastProgress = signature
                }
                if (status in FINAL_TASK_STATUSES || status in HUMAN_ACTION_STATUSES) return Result.success()
                if (status !in MONITORED_TASK_STATUSES) {
                    delay(waitMs)
                } else {
                    delay(waitMs)
                    waitMs = (waitMs * 2).coerceAtMost(30_000L)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (http: HttpException) {
                if (http.code() in 400..499) {
                    Log.w("SoftwareTaskMonitor", "Task monitor got HTTP ${http.code()} for $taskId", http)
                    return Result.success()
                }
                delay(waitMs)
                waitMs = (waitMs * 2).coerceAtMost(30_000L)
            } catch (error: Exception) {
                Log.w("SoftwareTaskMonitor", "Could not poll task $taskId", error)
                delay(waitMs)
                waitMs = (waitMs * 2).coerceAtMost(30_000L)
            }
        }
        SoftwareTaskMonitor.continueMonitoring(applicationContext, inputData)
        return Result.success()
    }
}
