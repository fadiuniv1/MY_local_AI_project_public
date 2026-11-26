package com.example.mylocalai.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mylocalai.model.QueuedPromptInfo

@Composable
fun ChatQueueDialog(
    items: List<QueuedPromptInfo>,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit,
    onCancel: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chat queue (${items.size} waiting)") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (items.isEmpty()) {
                    Text("No queued prompts. Send another prompt while one is running to add it here.")
                }
                items.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${index + 1}. ",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = item.text,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onMoveUp(item.assistantId) }, enabled = index > 0) {
                            Text("↑")
                        }
                        TextButton(
                            onClick = { onMoveDown(item.assistantId) },
                            enabled = index < items.lastIndex,
                        ) { Text("↓") }
                        TextButton(onClick = { onCancel(item.assistantId) }) { Text("✕") }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Prompts run one after another. The queue survives app restarts.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
