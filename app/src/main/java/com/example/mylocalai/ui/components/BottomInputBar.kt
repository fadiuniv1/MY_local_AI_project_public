package com.example.mylocalai.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import com.example.mylocalai.R
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.unit.dp

data class MacroShortcut(
    val icon: String,
    val label: String,
    val prompt: String
)

val defaultMacroShortcuts = listOf(
    MacroShortcut("🔄", "Restart Ollama", "Run command: sudo systemctl restart ollama"),
    MacroShortcut("🐳", "Prune Docker", "Run command: docker system prune -f"),
    MacroShortcut("📊", "Check Disk", "Run command: df -h /"),
    MacroShortcut("🧹", "Clear Temp Files", "Run command: rm -rf ~/agent-bridge/tmp/*"),
    MacroShortcut("⚡", "Restart Agent Bridge", "Run command: sudo systemctl restart agent-bridge.service")
)

@Composable
fun BottomInputBar(
    inputText: String,
    isStreaming: Boolean,
    isUploading: Boolean = false,
    uploadProgress: Float? = null,
    onInputTextChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onStopClick: () -> Unit,
    onMacroClick: (String) -> Unit,
    onAttachFileClick: () -> Unit,
    onAttachImageClick: () -> Unit = {},
    pendingImageCount: Int = 0,
    onClearImages: () -> Unit = {},
    onVoiceClick: () -> Unit = {},
    onPlanClick: () -> Unit = {},
    macros: List<MacroShortcut> = defaultMacroShortcuts,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            uploadProgress?.let { progress ->
                LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            }
            if (pendingImageCount > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("$pendingImageCount image${if (pendingImageCount == 1) "" else "s"} attached", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.weight(1f))
                    Text("Remove", modifier = Modifier.clickable(onClick = onClearImages), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                }
            }
            // One-tap macros (confirmed before any command runs).
            if (!isStreaming && !isUploading && macros.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState)
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onPlanClick, enabled = inputText.isNotBlank() && !isStreaming) {
                        Text("📋 " + stringResource(R.string.plan_button))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.macro_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                    macros.forEach { macro ->
                        AssistChip(
                            onClick = { onMacroClick(macro.prompt) },
                            label = {
                                Text(
                                    "${macro.icon} ${macro.label}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                }
            }

            // Input TextField & Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // File Attachment Button
                IconButton(
                    onClick = onAttachFileClick,
                    enabled = !isUploading && !isStreaming
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Attach File to Workspace",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                IconButton(onClick = onAttachImageClick, enabled = !isUploading && !isStreaming) {
                    Icon(Icons.Default.Image, contentDescription = "Attach image to chat", tint = MaterialTheme.colorScheme.primary)
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(onClick = onVoiceClick, enabled = !isStreaming && !isUploading) {
                    Icon(Icons.Default.Mic, contentDescription = "Dictate command", tint = MaterialTheme.colorScheme.primary)
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = onInputTextChange,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.input_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    maxLines = 5,
                    minLines = 1,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSendClick() }),
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                if (isStreaming) {
                    IconButton(
                        onClick = onStopClick,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop Generating",
                            tint = Color.White
                        )
                    }
                } else {
                    IconButton(
                        onClick = onSendClick,
                        enabled = inputText.isNotBlank(),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Message",
                            tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
