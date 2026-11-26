package com.example.mylocalai.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mylocalai.model.JobDto
import com.example.mylocalai.ui.theme.SuccessGreen

@Composable
fun JobsDialog(
    jobs: List<JobDto>,
    loading: Boolean,
    error: String?,
    onCancel: (String) -> Unit,
    retryableCommand: String? = null,
    retrySecondsRemaining: Int? = null,
    retryDelaySeconds: Int = 10,
    onRetryAfterDelay: () -> Unit = {},
    onCancelRetry: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Background Jobs",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Monitored over HTTP · Survives disconnects",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.78f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (loading && jobs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 2.5.dp)
                    }
                }

                error?.let {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                if (retryableCommand != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Server queue at capacity — command can be auto-retried:",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Surface(
                                color = Color.Black.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = retryableCommand,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    modifier = Modifier.padding(6.dp),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AssistChip(
                                    onClick = onRetryAfterDelay,
                                    enabled = retrySecondsRemaining == null,
                                    label = { Text(retrySecondsRemaining?.let { "Retrying in ${it}s…" } ?: "Retry in ${retryDelaySeconds}s") }
                                )
                                if (retrySecondsRemaining != null) {
                                    AssistChip(
                                        onClick = onCancelRetry,
                                        label = { Text("Cancel retry") },
                                        colors = AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.error)
                                    )
                                }
                            }
                        }
                    }
                }

                if (!loading && error == null && jobs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("No active or recent background jobs.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(jobs, key = { it.job_id }) { job ->
                            JobItemCard(
                                job = job,
                                onCancel = onCancel,
                                onCopy = { text ->
                                    clipboardManager.setText(AnnotatedString(text))
                                    Toast.makeText(context, "Job logs copied to clipboard", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

@Composable
private fun JobItemCard(
    job: JobDto,
    onCancel: (String) -> Unit,
    onCopy: (String) -> Unit
) {
    val status = job.status.lowercase()
    val active = status in setOf("queued", "running")
    val failed = status in setOf("failed", "cancelled", "error") || (job.exit_code != null && job.exit_code != 0)
    val success = status in setOf("completed", "finished", "success") || (job.exit_code == 0)

    val statusColor = when {
        active -> MaterialTheme.colorScheme.primary
        failed -> MaterialTheme.colorScheme.error
        success -> SuccessGreen
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val statusIcon = when {
        active -> Icons.Default.Schedule
        failed -> Icons.Default.Error
        success -> Icons.Default.CheckCircle
        else -> Icons.Default.Terminal
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header: Status badge & ID & Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = statusColor.copy(alpha = 0.15f),
                        shape = CircleShape
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = statusIcon,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = job.status.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = statusColor
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    Text(
                        text = "#${job.job_id.take(8)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (job.stdout.isNotBlank() || job.stderr.isNotBlank()) {
                        IconButton(
                            onClick = { onCopy("${job.stdout}\n${job.stderr}".trim()) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy logs",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    if (active) {
                        Spacer(Modifier.width(4.dp))
                        TextButton(
                            onClick = { onCancel(job.job_id) },
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Cancel", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // Command snippet
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$ ",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = job.command,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Terminal Console Box
            val outputText = buildString {
                if (job.stdout.isNotBlank()) append(job.stdout)
                if (job.stderr.isNotBlank()) {
                    if (isNotEmpty()) append("\n")
                    append("[stderr]\n")
                    append(job.stderr)
                }
            }.trim()

            if (outputText.isNotBlank()) {
                Surface(
                    color = Color(0xFF0F111A), // Sleek terminal dark
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TERMINAL OUTPUT",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF7982A9)
                                )
                            )
                            if (job.exit_code != null) {
                                Text(
                                    text = "exit: ${job.exit_code}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (job.exit_code == 0) SuccessGreen else MaterialTheme.colorScheme.error
                                    )
                                )
                            }
                        }
                        HorizontalDivider(
                            color = Color(0xFF23283B),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp)
                                .verticalScroll(rememberScrollState())
                                .horizontalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = outputText.takeLast(3_000),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    color = Color(0xFFCDD6F4)
                                )
                            )
                        }
                    }
                }
            }

            job.error?.let {
                Text(
                    text = "Error: $it",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
