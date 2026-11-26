package com.example.mylocalai.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.workDataOf
import com.example.mylocalai.MainActivity
import com.example.mylocalai.data.remote.RetrofitClient
import com.example.mylocalai.data.remote.JobResponseParser
import retrofit2.HttpException
import com.example.mylocalai.data.PreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

private const val KEY_BASE_URL = "base_url"
private const val KEY_JOB_ID = "job_id"
private const val CHANNEL_ID = "agent_job_updates"
private const val POLL_SEGMENT_MS = 7 * 60 * 1000L
private const val JOB_NAME_PREFIX = "agent-job-monitor-"

object JobCompletionMonitor {
    fun enqueue(context: Context, baseUrl: String, jobId: String) {
        val input = workDataOf(KEY_BASE_URL to baseUrl, KEY_JOB_ID to jobId)
        val request = OneTimeWorkRequestBuilder<JobCompletionWorker>()
            .setInputData(input)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork(
                JOB_NAME_PREFIX + jobId,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }.onFailure { Log.w("JobCompletionMonitor", "Could not schedule monitoring for $jobId", it) }
    }

    internal fun continueMonitoring(context: Context, input: Data) {
        val jobId = input.getString(KEY_JOB_ID) ?: return
        val request = OneTimeWorkRequestBuilder<JobCompletionWorker>()
            .setInputData(input)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork(
                JOB_NAME_PREFIX + jobId,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request,
            )
        }.onFailure { Log.w("JobCompletionMonitor", "Could not continue monitoring $jobId", it) }
    }
}

class JobCompletionWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val baseUrl = inputData.getString(KEY_BASE_URL) ?: return Result.failure()
        val jobId = inputData.getString(KEY_JOB_ID) ?: return Result.failure()
        val api = runCatching { RetrofitClient.createApiService(baseUrl) }.getOrElse { return Result.retry() }
        val startedAt = System.currentTimeMillis()
        var pollDelayMs = 1_500L

        while (System.currentTimeMillis() - startedAt < POLL_SEGMENT_MS) {
            try {
                val job = api.getJob(jobId).use { JobResponseParser.parse(it.string()) }
                if (job != null && job.status.lowercase() in TERMINAL_STATUSES) {
                    runCatching {
                        PreferencesRepository(applicationContext).recordBridgeAudit(
                            action = "Background job ${job.status}: ${job.command}",
                            detail = listOfNotNull(job.error, job.stderr, job.stdout, job.exit_code?.let { "Exit code: $it" }).filter { it.isNotBlank() }.joinToString("\n"),
                            succeeded = job.status.equals("completed", ignoreCase = true) && (job.exit_code == null || job.exit_code == 0),
                        )
                    }
                    postCompletionNotification(jobId, job.status, job.command, job.exit_code, job.stdout, job.stderr)
                    return Result.success()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (http: HttpException) {
                if (http.code() in 400..499) {
                    runCatching {
                        PreferencesRepository(applicationContext).recordBridgeAudit(
                            action = "Could not monitor job $jobId",
                            detail = http.message(),
                            succeeded = false,
                        )
                    }
                    postCompletionNotification(jobId, "monitoring_error", "Job $jobId", null, "", http.message())
                    return Result.success()
                }
            } catch (_: Exception) {
                // Keep monitoring through brief reconnects; commands are never resubmitted.
            }
            delay(pollDelayMs)
            pollDelayMs = (pollDelayMs * 2).coerceAtMost(10_000L)
        }

        // WorkManager workers have a finite execution window. Chain another monitor segment
        // so jobs with long timeouts continue to be watched after the app is backgrounded.
        JobCompletionMonitor.continueMonitoring(applicationContext, inputData)
        return Result.success()
    }

    private fun postCompletionNotification(
        jobId: String,
        status: String,
        command: String,
        exitCode: Int?,
        stdout: String,
        stderr: String,
    ) {
        try {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Ubuntu job updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Notifications when remote Ubuntu jobs finish"
                }
            )
        }

        val launchIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("completed_job_id", jobId)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            jobId.hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val output = (stderr.ifBlank { stdout }).trim().takeLast(220).takeIf { it.isNotBlank() }
        val detail = buildString {
            append(command.ifBlank { "Remote command" })
            exitCode?.let { append(" · exit ").append(it) }
            output?.let { append("\n").append(it) }
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Ubuntu job ${status.replace('_', ' ')}")
            .setContentText(command.ifBlank { "Job $jobId has finished." })
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(jobId.hashCode(), notification)
        } catch (error: SecurityException) {
            Log.w("JobCompletionMonitor", "Notification permission is unavailable", error)
        } catch (error: Exception) {
            Log.w("JobCompletionMonitor", "Could not post job completion notification", error)
        }
    }

    private companion object {
        val TERMINAL_STATUSES = setOf("completed", "failed", "timed_out", "cancelled", "interrupted")
    }
}
