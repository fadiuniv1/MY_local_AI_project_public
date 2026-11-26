package com.example.mylocalai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.mylocalai.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mylocalai.model.ConnectionMode

@Composable
fun SettingsDialog(
    currentMode: ConnectionMode,
    currentAgentUrl: String,
    currentPureChatUrl: String,
    currentSystemPrompt: String,
    currentModel: String,
    currentTerminalTheme: String,
    availableModels: List<String> = emptyList(),
    forceLegacyChat: Boolean = false,
    onForceLegacyChatChange: (Boolean) -> Unit = {},
    delegateLongWork: Boolean = false,
    onDelegateLongWorkChange: (Boolean) -> Unit = {},
    notifyReplies: Boolean = true,
    onNotifyRepliesChange: (Boolean) -> Unit = {},
    notifyDownloads: Boolean = true,
    onNotifyDownloadsChange: (Boolean) -> Unit = {},
    notifyTasks: Boolean = true,
    onNotifyTasksChange: (Boolean) -> Unit = {},
    allowedTools: Set<String> = emptySet(),
    onToolAllowedChange: (String, Boolean) -> Unit = { _, _ -> },
    language: String = "system",
    onLanguageChange: (String) -> Unit = {},
    onRefreshModels: () -> Unit = {},
    onDismiss: () -> Unit,
    onSave: (mode: ConnectionMode, agentUrl: String, pureChatUrl: String, systemPrompt: String, model: String, terminalTheme: String) -> Unit
) {
    var mode by remember { mutableStateOf(currentMode) }
    var agentUrl by remember { mutableStateOf(currentAgentUrl) }
    var pureChatUrl by remember { mutableStateOf(currentPureChatUrl) }
    var systemPrompt by remember { mutableStateOf(currentSystemPrompt) }
    var model by remember { mutableStateOf(currentModel) }
    var terminalTheme by remember { mutableStateOf(currentTerminalTheme) }
    var modelMenuOpen by remember { mutableStateOf(false) }
    var themeMenuOpen by remember { mutableStateOf(false) }

    // Aggregate models: available models from server + current model + standard fallbacks
    val displayModels = remember(availableModels, model) {
        val list = mutableListOf<String>()
        if (availableModels.isNotEmpty()) {
            list.addAll(availableModels)
        } else {
            list.addAll(listOf("qwen3.5:4b", "llama3.2:3b", "qwen2.5-coder:1.5b", "qwen2.5-coder:32b"))
        }
        if (model.isNotBlank() && model !in list) {
            list.add(0, model)
        }
        list.distinct()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Connection & Model Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section 1: Model & Theme Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Model & Appearance", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }

                        // Default model selector
                        Text("Default model", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.weight(1f)) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { modelMenuOpen = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = model.ifBlank { "Select model" },
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                DropdownMenu(
                                    expanded = modelMenuOpen,
                                    onDismissRequest = { modelMenuOpen = false }
                                ) {
                                    for (opt in displayModels) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = opt,
                                                    fontWeight = if (opt == model) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            onClick = {
                                                model = opt
                                                modelMenuOpen = false
                                            }
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            IconButton(onClick = onRefreshModels) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh models from server", tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        // Terminal color theme selector
                        Text("Terminal color theme", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { themeMenuOpen = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.ColorLens, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(8.dp))
                                        Text(terminalTheme, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            DropdownMenu(
                                expanded = themeMenuOpen,
                                onDismissRequest = { themeMenuOpen = false }
                            ) {
                                listOf("Dracula", "Catppuccin", "Classic Matrix Green").forEach { opt ->
                                    DropdownMenuItem(
                                        text = { Text(opt, fontWeight = if (opt == terminalTheme) FontWeight.Bold else FontWeight.Normal) },
                                        onClick = {
                                            terminalTheme = opt
                                            themeMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Section 2: Connection Mode Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Connection Mode", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }

                        // Agent Mode Radio
                        Surface(
                            color = if (mode == ConnectionMode.AGENT) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { mode = ConnectionMode.AGENT }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                RadioButton(
                                    selected = mode == ConnectionMode.AGENT,
                                    onClick = { mode = ConnectionMode.AGENT },
                                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                )
                                Column(modifier = Modifier.padding(start = 6.dp)) {
                                    Text(
                                        text = "Agent Mode (Ubuntu Tools)",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "FastAPI Bridge on port 8081 (Executes file/shell tools)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Pure Chat Mode Radio
                        Surface(
                            color = if (mode == ConnectionMode.PURE_CHAT) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { mode = ConnectionMode.PURE_CHAT }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                RadioButton(
                                    selected = mode == ConnectionMode.PURE_CHAT,
                                    onClick = { mode = ConnectionMode.PURE_CHAT },
                                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                )
                                Column(modifier = Modifier.padding(start = 6.dp)) {
                                    Text(
                                        text = "Pure Chat Mode (Ollama Direct)",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Ollama Native endpoint on port 11434 (Standard text streaming)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Section 3: Server Endpoints Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Server Endpoints", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                        OutlinedTextField(
                            value = agentUrl,
                            onValueChange = { agentUrl = it },
                            label = { Text("Agent Base URL (Port 8081)") },
                            placeholder = { Text("http://<tailscale-ip>:8081") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = pureChatUrl,
                            onValueChange = { pureChatUrl = it },
                            label = { Text("Pure Chat Base URL (Port 11434)") },
                            placeholder = { Text("http://<tailscale-ip>:11434") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Section 4: System Prompt Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("System Prompt", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                        OutlinedTextField(
                            value = systemPrompt,
                            onValueChange = { systemPrompt = it },
                            placeholder = { Text("You are a concise, expert coding & creation assistant.") },
                            minLines = 2,
                            maxLines = 4,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Delegate long work to a durable background task (off by default: work stays in chat).
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Delegate long work (experimental)", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Switch(checked = delegateLongWork, onCheckedChange = onDelegateLongWorkChange)
                    }
                    Text(
                        "Background tasks are unreliable on this host. Keep off to do work inline in chat.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Language / RTL
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("system" to R.string.lang_system, "en" to R.string.lang_en,
                            "he" to R.string.lang_he, "ar" to R.string.lang_ar).forEach { (code, label) ->
                            AssistChip(
                                onClick = { onLanguageChange(code) },
                                label = { Text(stringResource(label)) },
                                enabled = code != language,
                            )
                        }
                    }
                }

                // Tool permissions
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Allowed agent tools", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    val toolNames = listOf(
                        "read_file", "list_files", "write_file", "run_command", "transfer_file",
                        "web_search", "research", "fetch_url", "get_system_status", "network_diagnostics",
                    )
                    toolNames.forEach { name ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Switch(
                                checked = name in allowedTools,
                                onCheckedChange = { onToolAllowedChange(name, it) }
                            )
                        }
                    }
                }

                // Notifications (Android 13+ permission is requested on first launch)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Notifications", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Agent replies", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Switch(checked = notifyReplies, onCheckedChange = onNotifyRepliesChange)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("File downloads", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Switch(checked = notifyDownloads, onCheckedChange = onNotifyDownloadsChange)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Software tasks", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Switch(checked = notifyTasks, onCheckedChange = onNotifyTasksChange)
                    }
                }

                // Cloud models are opt-in on the bridge via env config.
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Cloud models", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    Text(
                        "If the bridge is configured with CLOUD_BASE_URL and an API key, " +
                            "cloud:fast and cloud:strong appear in the model list. " +
                            "No key means fully local Ollama only.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Debug: force legacy synchronous chat (buried in settings, not the composer)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Debug: force legacy /api/chat", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Switch(checked = forceLegacyChat, onCheckedChange = onForceLegacyChatChange)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(mode, agentUrl, pureChatUrl, systemPrompt, model, terminalTheme) }
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
