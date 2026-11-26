package com.example.mylocalai.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.mylocalai.MainActivity

object AppVisibility {
    @Volatile
    var isForeground: Boolean = false
}

object AgentNotifications {
    private const val CHANNEL_ID = "agent_replies"
    private const val TASK_CHANNEL_ID = "software_task_progress"
    private const val DOWNLOAD_CHANNEL_ID = "file_downloads"

    /** Called once from Application.onCreate — never create channels per notification. */
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Agent replies", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Alerts when a remote AI response arrives while the app is in the background"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(TASK_CHANNEL_ID, "Software task progress", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Progress updates for persistent Agent Bridge software tasks"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(DOWNLOAD_CHANNEL_ID, "File downloads", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Alerts when a PC file finishes downloading to the phone"
            }
        )
    }

    fun notifyDownload(context: Context, fileName: String, success: Boolean, detail: String) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notificationId = ("download:$fileName").hashCode()
            val notification = NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID)
                .setSmallIcon(if (success) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
                .setContentTitle(if (success) "Download complete" else "Download failed")
                .setContentText(if (success) "$fileName saved to Downloads" else "$fileName: ${detail.take(120)}")
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            manager.notify(notificationId, notification)
        } catch (error: SecurityException) {
            Log.w("AgentNotifications", "Notification permission is unavailable", error)
        } catch (error: Exception) {
            Log.w("AgentNotifications", "Could not post download notification", error)
        }
    }

    fun notifyReply(
        context: Context,
        sessionId: String,
        prompt: String,
        answer: String,
        requestId: String? = null,
        baseUrl: String? = null,
    ) {
        try {
            val notificationId = (System.currentTimeMillis() and 0x7fffffff).toInt()
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("notification_session_id", sessionId)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle("Your Ubuntu request is finished")
                .setContentText(answer.trim().replace('\n', ' ').take(150).ifBlank { "Your request has finished." })
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "Request: ${prompt.trim().take(180)}\n\n${answer.trim().take(1200)}"
                    )
                )
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            if (!requestId.isNullOrBlank() && !baseUrl.isNullOrBlank()) {
                val stopIntent = Intent(context, ChatJobActionReceiver::class.java).apply {
                    putExtra("chat_job_id", requestId)
                    putExtra("chat_job_base_url", baseUrl)
                }
                val stopPending = PendingIntent.getBroadcast(
                    context,
                    notificationId,
                    stopIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                builder.addAction(android.R.drawable.ic_delete, "Stop", stopPending)
            }
            val notification = builder.build()
            manager.notify(notificationId, notification)
        } catch (error: SecurityException) {
            Log.w("AgentNotifications", "Notification permission is unavailable", error)
        } catch (error: Exception) {
            Log.w("AgentNotifications", "Could not post reply notification", error)
        }
    }

    fun notifySoftwareTaskProgress(
        context: Context,
        taskId: String,
        objective: String,
        status: String,
        phase: String,
        stepsDone: Int?,
        stepsTotal: Int?,
    ) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("software_task_id", taskId)
            }
            val requestCode = taskId.hashCode()
            val pendingIntent = PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val progress = if (stepsDone != null || stepsTotal != null) " · ${stepsDone ?: 0}/${stepsTotal ?: "?"} steps" else ""
            val terminal = status.lowercase() in setOf("completed", "failed", "cancelled", "paused", "needs_input", "interrupted")
            val notification = NotificationCompat.Builder(context, TASK_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(if (terminal) "Software task ${status.lowercase()}" else "Software task progress · ${status.lowercase()}")
                .setContentText(listOf(objective.take(80), phase.takeIf(String::isNotBlank), progress.removePrefix(" · ").takeIf(String::isNotBlank)).filterNotNull().joinToString(" · "))
                .setStyle(NotificationCompat.BigTextStyle().bigText("$objective\nStatus: $status\nPhase: ${phase.ifBlank { "—" }}$progress\nTask ID: $taskId"))
                .setContentIntent(pendingIntent)
                .setAutoCancel(terminal)
                .setOngoing(!terminal)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            manager.notify(requestCode, notification)
        } catch (error: SecurityException) {
            Log.w("AgentNotifications", "Notification permission is unavailable", error)
        } catch (error: Exception) {
            Log.w("AgentNotifications", "Could not post software task progress", error)
        }
    }
}
