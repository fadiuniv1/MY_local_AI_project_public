package com.example.mylocalai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mylocalai.model.ServerHealthMetrics
import com.example.mylocalai.model.SoftwareTaskSummary
import com.example.mylocalai.model.SystemStats

@Composable
fun SystemStatsDialog(
    stats: SystemStats?,
    isLoading: Boolean,
    serverMetrics: ServerHealthMetrics? = null,
    serverMetricsLoading: Boolean = false,
    serverMetricsError: String? = null,
    activeChatJobElapsedSeconds: Long? = null,
    tasks: List<SoftwareTaskSummary> = emptyList(),
    onRefresh: () -> Unit,
    onViewServerLogs: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = "System Stats",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Ubuntu PC Stats",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onViewServerLogs, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "View Server Logs",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onRefresh, enabled = !isLoading, modifier = Modifier.size(32.dp)) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Stats",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        },
        text = {
            if (stats == null && isLoading) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Fetching PC metrics...")
                }
            } else if (stats != null || serverMetrics != null || serverMetricsLoading) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Server health metrics (health endpoint).
                    serverMetrics?.let { metrics ->
                        metrics.availableMemoryBytes?.let {
                            StatItemCard(
                                icon = Icons.Default.Memory,
                                title = "Available RAM",
                                value = formatBytes(it)
                            )
                        }
                        metrics.diskFreeBytes?.let {
                            StatItemCard(
                                icon = Icons.Default.SdStorage,
                                title = "Disk free",
                                value = if (metrics.diskFreePercent != null) "${formatBytes(it)} (${metrics.diskFreePercent}%)" else formatBytes(it),
                                percent = metrics.diskFreePercent?.let { percent -> percent / 100f }
                            )
                        }
                        metrics.chatJobsPending?.let {
                            StatItemCard(
                                icon = Icons.Default.History,
                                title = "Chat jobs pending",
                                value = "$it"
                            )
                        }
                        if (tasks.isNotEmpty()) {
                            val summary = tasks.groupBy { it.status.lowercase() }
                                .map { (status, items) -> "${status.replaceFirstChar { it.uppercaseChar() }}: ${items.size}" }
                                .sorted()
                                .joinToString(", ")
                            StatItemCard(
                                icon = Icons.Default.List,
                                title = "Software tasks",
                                value = summary
                            )
                        }
                        activeChatJobElapsedSeconds?.let { elapsed ->
                            StatItemCard(
                                icon = Icons.Default.Timer,
                                title = "Job elapsed",
                                value = formatDuration(elapsed)
                            )
                        }
                    }
                    if (stats == null && serverMetricsLoading && serverMetrics == null) {
                        CircularProgressIndicator(modifier = Modifier.padding(vertical = 8.dp))
                    }
                    serverMetricsError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    // System stats (stats endpoint).
                    if (stats != null) {
                        // The bridge guarantees uptime, memory and disk; CPU is optional.
                        StatItemCard(
                            icon = Icons.Default.Speed,
                            title = "System uptime",
                            value = stats.uptime,
                            subtitle = stats.cpuUsage.takeIf { it != "N/A" }?.let { "CPU usage: $it" }
                        )

                        // RAM Usage
                        StatItemCard(
                            icon = Icons.Default.Memory,
                            title = "RAM Memory",
                            value = formatMetric(stats.ramUsed, stats.ramTotal, stats.ramPercent),
                            percent = parsePercent(stats.ramPercent)
                        )

                        // Disk Usage
                        StatItemCard(
                            icon = Icons.Default.SdStorage,
                            title = "Disk Space",
                            value = formatMetric(stats.diskUsed, stats.diskTotal, stats.diskPercent),
                            percent = parsePercent(stats.diskPercent)
                        )
                    } else if (serverMetrics == null) {
                        Text(
                            text = "Failed to fetch system stats from Ubuntu host.",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            } else {
                Text(
                    text = "Failed to fetch system stats from Ubuntu host.",
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun StatItemCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    subtitle: String? = null,
    percent: Float? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (percent != null) {
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { percent.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = if (percent > 0.85f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }

        if (subtitle != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun parsePercent(str: String): Float? {
    return try {
        val clean = str.replace("%", "").trim()
        clean.toFloat() / 100f
    } catch (_: Exception) {
        null
    }
}

private fun formatBytes(bytes: Long?): String {
    if (bytes == null) return "N/A"
    return when {
        bytes >= 1024L * 1024 * 1024 -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
        bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
        bytes >= 1024L -> "${bytes / 1024} KB"
        else -> "$bytes B"
    }
}

private fun formatDuration(seconds: Long): String = when {
    seconds >= 3600 -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    seconds >= 60 -> "${seconds / 60}m ${seconds % 60}s"
    else -> "${seconds}s"
}

private fun formatMetric(used: String, total: String, percent: String): String = when {
    used == "N/A" -> "N/A"
    total == "N/A" && percent == "N/A" -> used
    else -> "$used / $total (${percent})"
}
