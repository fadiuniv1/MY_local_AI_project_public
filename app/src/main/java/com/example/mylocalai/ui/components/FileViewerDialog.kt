package com.example.mylocalai.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mylocalai.model.FilePreviewState

@Composable
fun FileViewerDialog(
    previewState: FilePreviewState,
    onCopyContent: (String) -> Unit,
    onDeleteFile: (String) -> Unit,
    onDownloadClick: (String) -> Unit = {},
    onRetryDownload: () -> Unit = {},
    onAskAboutImage: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()
    var confirmDelete by remember(previewState.filePath) { mutableStateOf(false) }
    val imageBitmap = remember(previewState.imageBytes) {
        previewState.imageBytes?.let { bytes ->
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.RGB_565
                })
            }.getOrNull()
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
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = "File Viewer",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = previewState.filePath.ifBlank { "Workspace File" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onDownloadClick(previewState.filePath) },
                        enabled = !previewState.isLoading,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Save file to phone",
                            tint = if (!previewState.isLoading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (!previewState.isLoading && previewState.fileContent.isNotBlank()) {
                        IconButton(
                            onClick = { onCopyContent(previewState.fileContent) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy File Content",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete File",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        },
        text = {
            if (previewState.isLoading) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Downloading file from Ubuntu PC...")
                    previewState.downloadProgress?.let { progress ->
                        LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        Text("${(progress.coerceIn(0f, 1f) * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                    }
                }
            } else if (previewState.errorMessage != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(previewState.errorMessage, color = MaterialTheme.colorScheme.error)
                    if (previewState.canRetry) TextButton(onClick = onRetryDownload) { Text("Retry download") }
                }
            } else if (imageBitmap != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Image(
                        bitmap = imageBitmap.asImageBitmap(),
                        contentDescription = previewState.filePath,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)
                    )
                    TextButton(onClick = onAskAboutImage) { Text("Ask about screenshot") }
                }
            } else if (previewState.fileContent.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0D1117))
                        .padding(12.dp)
                ) {
                    Box(modifier = Modifier.horizontalScroll(scrollState).verticalScroll(rememberScrollState())) {
                        SelectionContainer {
                            Text(
                                text = previewState.fileContent.trimEnd(),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                ),
                                color = Color(0xFFA6E3A1)
                            )
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Preview isn’t available for this file type.", style = MaterialTheme.typography.bodyMedium)
                    Text("${previewState.mimeType} · ${previewState.byteCount} bytes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(
                    onClick = { onDownloadClick(previewState.filePath) },
                    enabled = !previewState.isLoading
                ) {
                    Text("Save to phone")
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete remote file?") },
            text = { Text("This permanently deletes ${previewState.filePath} from the Ubuntu workstation.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDeleteFile(previewState.filePath)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep file") } },
        )
    }
}
