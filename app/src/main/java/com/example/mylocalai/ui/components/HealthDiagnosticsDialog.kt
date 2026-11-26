package com.example.mylocalai.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mylocalai.model.SystemStats

@Composable
fun HealthDiagnosticsDialog(
    loading: Boolean,
    result: String?,
    error: String?,
    stats: SystemStats?,
    selectedModel: String,
    agentBaseUrl: String,
    availableModels: List<String>,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agent Bridge health") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                if (loading) CircularProgressIndicator()
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text("Ubuntu memory", style = MaterialTheme.typography.titleSmall)
                Text(
                    stats?.let { metric ->
                        when {
                            metric.ramUsed != "N/A" && metric.ramTotal != "N/A" -> "${metric.ramUsed} / ${metric.ramTotal}${if (metric.ramPercent != "N/A") " (${metric.ramPercent})" else ""}"
                            metric.ramUsed != "N/A" -> metric.ramUsed
                            else -> "Memory metrics unavailable"
                        }
                    } ?: "Loading memory metrics…",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text("Ollama settings", style = MaterialTheme.typography.titleSmall)
                Text("Selected model: $selectedModel", style = MaterialTheme.typography.bodyMedium)
                Text("Agent Bridge: $agentBaseUrl", style = MaterialTheme.typography.bodySmall)
                Text(
                    "Installed models: ${availableModels.takeIf { it.isNotEmpty() }?.joinToString() ?: "Not discovered"}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text("Bridge health", style = MaterialTheme.typography.titleSmall)
                result?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), maxLines = 80, overflow = TextOverflow.Clip)
                }
                if (!loading && result == null && error == null) Text("No health response yet.")
            }
        },
        confirmButton = { TextButton(onClick = onRefresh, enabled = !loading) { Text("Refresh") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
