package com.example.mylocalai.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.Text
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.compose.ui.unit.dp
import androidx.glance.currentState
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.mylocalai.data.remote.RetrofitClient
import com.example.mylocalai.data.PreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private val UptimeKey = stringPreferencesKey("uptime")
private val CpuKey = stringPreferencesKey("cpu")
private val MemoryKey = stringPreferencesKey("memory")
private val DiskKey = stringPreferencesKey("disk")

class UbuntuStatsWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val state = currentState<Preferences>()
            Column(modifier = GlanceModifier.fillMaxSize().padding(12.dp)) {
                Text("Ubuntu workstation")
                state[CpuKey]?.takeIf { it != "N/A" }?.let { Text("CPU: $it") }
                Text("Uptime: ${state[UptimeKey] ?: "Updating…"}")
                Text("Memory: ${state[MemoryKey] ?: "—"}")
                Text("Disk: ${state[DiskKey] ?: "—"}")
            }
        }
    }
}

class UbuntuStatsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UbuntuStatsWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        val work = PeriodicWorkRequestBuilder<UbuntuStatsRefreshWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "ubuntu_stats_widget_refresh", ExistingPeriodicWorkPolicy.UPDATE, work
        )
        WorkManager.getInstance(context).enqueue(
            androidx.work.OneTimeWorkRequestBuilder<UbuntuStatsRefreshWorker>().build()
        )
    }
}

class UbuntuStatsRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val baseUrl = PreferencesRepository(applicationContext).agentBaseUrlFlow.first()
            val stats = RetrofitClient.createApiService(baseUrl).getStats()
            val manager = androidx.glance.appwidget.GlanceAppWidgetManager(applicationContext)
            manager.getGlanceIds(UbuntuStatsWidget::class.java).forEach { id ->
                updateAppWidgetState(applicationContext, id) { prefs ->
                    prefs[UptimeKey] = stats.uptime
                    prefs[CpuKey] = stats.cpu
                    prefs[MemoryKey] = stats.memory
                    prefs[DiskKey] = stats.disk
                }
                UbuntuStatsWidget().update(applicationContext, id)
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
