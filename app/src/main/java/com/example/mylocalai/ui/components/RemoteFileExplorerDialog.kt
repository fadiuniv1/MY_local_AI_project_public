package com.example.mylocalai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mylocalai.model.RemoteFileEntry

@Composable
fun RemoteFileExplorerDialog(
    path: String,
    entries: List<RemoteFileEntry>,
    isLoading: Boolean,
    error: String?,
    truncated: Boolean = false,
    elevated: Boolean = false,
    asRoot: Boolean = false,
    onNavigateTo: (String) -> Unit = {},
    onNavigateUp: () -> Unit,
    onRefresh: () -> Unit,
    onEntryClick: (RemoteFileEntry) -> Unit,
    onDownloadClick: (RemoteFileEntry) -> Unit = {},
    onUploadClick: () -> Unit = {},
    onCopyClick: (RemoteFileEntry) -> Unit = {},
    onMoveClick: (RemoteFileEntry) -> Unit = {},
    onDeleteClick: (RemoteFileEntry) -> Unit = {},
    onToggleRoot: (Boolean) -> Unit = {},
    onDismiss: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var sortByName by remember { mutableStateOf(true) }

    val filteredEntries = remember(entries, searchQuery, sortByName) {
        val query = searchQuery.trim().lowercase()
        val list = if (query.isEmpty()) {
            entries
        } else {
            entries.filter { it.name.lowercase().contains(query) }
        }
        // Folders first, then by name or size.
        list.sortedWith(
            compareBy<RemoteFileEntry> { !it.isDirectory }
                .thenBy { if (sortByName) it.name.lowercase() else "${it.size ?: -1}" }
        )
    }

    val folderCount = remember(entries) { entries.count { it.isDirectory } }
    val fileCount = remember(entries) { entries.count { !it.isDirectory } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Remote Files · Whole PC",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$folderCount folders · $fileCount files",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh directory",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.78f)
            ) {
                // Path Bar & Up button
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onNavigateUp,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Go Up",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        val segments = path.trim().trimEnd('/').split('/').filter { it.isNotBlank() }
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AssistChip(
                                onClick = { onNavigateTo("/") },
                                label = {
                                    Text(
                                        "/",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)
                                    )
                                }
                            )
                            var accumulated = ""
                            segments.forEach { segment ->
                                accumulated = when {
                                    accumulated.isEmpty() && segment == "~" -> "~"
                                    accumulated.isEmpty() -> "/$segment"
                                    accumulated == "~" -> "~/$segment"
                                    else -> "$accumulated/$segment"
                                }
                                val target = accumulated
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                AssistChip(
                                    onClick = { onNavigateTo(target) },
                                    label = {
                                        Text(
                                            segment,
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Shortcuts and actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AssistChip(onClick = { onNavigateTo("~") }, label = { Text("Home") })
                    AssistChip(onClick = { onNavigateTo("~/agent-bridge") }, label = { Text("agent-bridge") })
                    AssistChip(
                        onClick = { sortByName = !sortByName },
                        label = { Text(if (sortByName) "Sort: name" else "Sort: size") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                    AssistChip(
                        onClick = { onToggleRoot(!asRoot) },
                        label = { Text(if (asRoot) "Root: on" else "Root: off") }
                    )
                    IconButton(onClick = onUploadClick) {
                        Icon(Icons.Default.Upload, contentDescription = "Upload to this folder", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                if (elevated || truncated) {
                    Text(
                        text = buildString {
                            if (elevated) append("Elevated access active.")
                            if (elevated && truncated) append(" ")
                            if (truncated) append("Listing truncated — narrow the path or reduce scope.")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Search Box
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            "Filter files...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear search",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Content list
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(strokeWidth = 2.5.dp)
                        }
                    }
                    error != null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    filteredEntries.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchQuery.isNotBlank()) "No files match \"$searchQuery\"" else "This directory is empty",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            items(filteredEntries, key = { it.path }) { entry ->
                                RemoteFileRowItem(
                                    entry = entry,
                                    onClick = { onEntryClick(entry) },
                                    onDownloadClick = { onDownloadClick(entry) },
                                    onCopyClick = { onCopyClick(entry) },
                                    onMoveClick = { onMoveClick(entry) },
                                    onDeleteClick = { onDeleteClick(entry) }
                                )
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
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
private fun RemoteFileRowItem(
    entry: RemoteFileEntry,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onCopyClick: () -> Unit = {},
    onMoveClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
) {
    val (icon, iconColor, badgeText) = getFileIconAndBadge(entry)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon container
        Surface(
            color = iconColor.copy(alpha = 0.15f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Name
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (entry.isDirectory) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Type badge or Chevron / Download action
        if (entry.isDirectory) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Open folder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (badgeText != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = onCopyClick, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy file", modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onMoveClick, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Folder, contentDescription = "Move file", modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onDeleteClick, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete file", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                }
                IconButton(
                    onClick = onDownloadClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download to phone",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun getFileIconAndBadge(entry: RemoteFileEntry): Triple<ImageVector, Color, String?> {
    if (entry.isDirectory) {
        return Triple(Icons.Default.Folder, Color(0xFFE5A93C), null) // Amber folder
    }

    val ext = entry.name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "py" -> Triple(Icons.Default.Code, Color(0xFF388E3C), "PY")
        "sh", "bash" -> Triple(Icons.Default.Terminal, Color(0xFF7B1FA2), "SH")
        "md" -> Triple(Icons.Default.Description, Color(0xFF0288D1), "MD")
        "log", "txt" -> Triple(Icons.Default.Description, Color(0xFF546E7A), ext.uppercase())
        "json", "yaml", "yml", "toml", "env", "conf" -> Triple(Icons.Default.Code, Color(0xFFE65100), ext.uppercase())
        "png", "jpg", "jpeg", "webp", "gif" -> Triple(Icons.Default.Image, Color(0xFF00897B), "IMG")
        else -> Triple(Icons.AutoMirrored.Filled.InsertDriveFile, Color(0xFF78909C), ext.take(4).uppercase().ifBlank { "FILE" })
    }
}
