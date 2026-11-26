package com.example.mylocalai.ui.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ServerLogsDialog(
    logs: String?,
    isLoading: Boolean,
    error: String? = null,
    currentLines: Int = 200,
    onSelectLines: (Int) -> Unit = {},
    onRefresh: () -> Unit,
    onCopyLogs: (String) -> Unit,
    saveMessage: String? = null,
    saveError: String? = null,
    onSaveLogs: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val hScrollState = rememberScrollState()
    val vScrollState = rememberScrollState()
    var searchQuery by remember { mutableStateOf("") }

    val rawLogs = logs ?: ""
    val lineList = remember(rawLogs) {
        if (rawLogs.isBlank()) emptyList() else rawLogs.lines()
    }

    val filteredLines = remember(lineList, searchQuery) {
        if (searchQuery.isBlank()) {
            lineList
        } else {
            lineList.filter { it.contains(searchQuery, ignoreCase = true) }
        }
    }

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
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Server Logs",
                        tint = Color(0xFF7EE787),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Ubuntu Server Logs",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "FastAPI & systemd agent-bridge output",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (logs != null) {
                        IconButton(onClick = { onCopyLogs(logs) }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Logs",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = onSaveLogs,
                        enabled = logs != null && !isLoading,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save server logs to phone",
                            tint = if (logs != null && !isLoading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onRefresh, enabled = !isLoading, modifier = Modifier.size(32.dp)) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Logs",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.80f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Line Depth Selector Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Lines:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(100, 250, 500, 1000).forEach { lineCount ->
                            val isSelected = currentLines == lineCount
                            Surface(
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                shape = CircleShape,
                                modifier = Modifier.clip(CircleShape)
                            ) {
                                TextButton(
                                    onClick = { onSelectLines(lineCount) },
                                    enabled = !isLoading,
                                    modifier = Modifier.height(28.dp).padding(horizontal = 4.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        text = "$lineCount",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Filter / Search Input
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filter logs (e.g. error, task, uvicorn)...", style = MaterialTheme.typography.bodySmall) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search", modifier = Modifier.size(14.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                )

                // Status info / Save messages
                saveMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall) }
                saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) }

                // Logs Display Area
                if (isLoading && logs == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Fetching $currentLines log lines from server...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else if (filteredLines.isNotEmpty()) {
                    Surface(
                        color = Color(0xFF0D1117),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            // Mini Status Bar in Console
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (searchQuery.isNotBlank()) {
                                        "${filteredLines.size} of ${lineList.size} lines match"
                                    } else {
                                        "${lineList.size} lines (${rawLogs.length / 1024} KB)"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        color = Color(0xFF8B949E),
                                        fontFamily = FontFamily.Monospace
                                    )
                                )
                                Text(
                                    text = "tail -n $currentLines",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        color = Color(0xFF58A6FF),
                                        fontFamily = FontFamily.Monospace
                                    )
                                )
                            }
                            HorizontalDivider(
                                color = Color(0xFF21262D),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .verticalScroll(vScrollState)
                                    .horizontalScroll(hScrollState)
                            ) {
                                SelectionContainer {
                                    Column {
                                        filteredLines.forEach { line ->
                                            val lineLower = line.lowercase()
                                            val lineColor = when {
                                                "error" in lineLower || "exception" in lineLower || "fatal" in lineLower -> Color(0xFFFF7B72)
                                                "warn" in lineLower -> Color(0xFFD29922)
                                                "http" in lineLower || "get /" in lineLower || "post /" in lineLower -> Color(0xFF79C0FF)
                                                else -> Color(0xFFA6E3A1)
                                            }
                                            Text(
                                                text = line,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 10.sp,
                                                    lineHeight = 14.sp
                                                ),
                                                color = lineColor
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No log lines match \"$searchQuery\"" else error ?: "No server logs available.",
                            color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onSaveLogs, enabled = logs != null && !isLoading) {
                    Text("Save to Phone")
                }
                TextButton(onClick = onDismiss) {
                    Text("Close", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    )
}
