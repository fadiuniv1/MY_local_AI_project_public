package com.example.mylocalai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mylocalai.model.AgentNoteDto

@Composable
fun AgentNotesDialog(
    notes: List<AgentNoteDto>,
    loading: Boolean,
    posting: Boolean,
    error: String?,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSync: () -> Unit,
    onPost: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Server agent notes") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Notes are shared discussion text. The app never executes note contents.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onSync, enabled = !loading) { Text("Sync new notes") }
                    if (loading) CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(notes, key = { if (it.id > 0) it.id else it.hashCode().toLong() }) { note ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("${note.source.ifBlank { "Agent" }} · ${note.category.ifBlank { "note" }}", style = MaterialTheme.typography.labelMedium)
                                if (note.created_at.isNotBlank()) Text(note.created_at, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(note.note, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    label = { Text("Implementation update or suggestion") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { TextButton(onClick = onPost, enabled = draft.isNotBlank() && !posting) { Text(if (posting) "Posting…" else "Post note") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
