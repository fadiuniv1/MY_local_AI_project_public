package com.example.mylocalai.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import com.example.mylocalai.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mylocalai.model.ChatMessage
import com.example.mylocalai.model.ToolResultDto
import com.example.mylocalai.ui.theme.AssistantBubbleColor
import com.example.mylocalai.ui.theme.UserBubbleColor

/** Extract distinct absolute PC file paths from agent prose, for inline preview chips. */
internal fun extractMentionedFilePaths(text: String): List<String> {
    return Regex("(/[A-Za-z0-9_./-]+\\.(?:html|yaml|json|csv|java|cpp|log|txt|kt|py|sh|md|css|js|ts|xml|yml|h))(?![A-Za-z0-9])")
        .findAll(text)
        .map { it.value.trimEnd('.', ',', ')', ']', '"', '\'') }
        .distinct()
        .take(8)
        .toList()
}

@Composable
fun ChatMessageItem(
    message: ChatMessage,
    onCopyCode: (String) -> Unit,
    onRunCode: (String) -> Unit = {},
    onResendPrompt: (String) -> Unit = {},
    terminalTheme: String = "Dracula",
    onToggleThinking: (String) -> Unit,
    onToggleAction: (String) -> Unit,
    onFileClick: (String) -> Unit = {},
    onOpenInBrowser: (String) -> Unit = {},
    onRetryClick: () -> Unit = {},
    onRetryStrongClick: (String) -> Unit = {},
    onDeleteClick: (String) -> Unit = {},
    onReviewClick: (String) -> Unit = {},
    onUndoWrite: (String, String) -> Unit = { _, _ -> },
    onDiagnoseClick: (String) -> Unit = {},
    onCopyClick: (String) -> Unit = {},
    onRegenerateClick: () -> Unit = {},
    onEditClick: (String) -> Unit = {},
    onOpenTask: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isUser = message.role == "user"
    // Only real job/HTTP failures set this flag — ordinary prose containing "error"
    // must not turn into a retry/diagnose prompt.
    val hasError = message.isJobFailure

    // Absolute PC paths mentioned in the final answer become tappable preview chips.
    val mentionedFiles = remember(message.content) { extractMentionedFilePaths(message.content) }
    // Also derive chips from real tool results, so files show even if the answer omits paths.
    val toolFiles = remember(message.toolResults) {
        message.toolResults.mapNotNull { result ->
            val obj = result.data?.takeIf { it.isJsonObject }?.asJsonObject
            obj?.get("path")?.takeIf { it.isJsonPrimitive }?.asString
                ?: obj?.get("saved_path")?.takeIf { it.isJsonPrimitive }?.asString
                ?: result.download_url
        }.filter { it.startsWith("/") }.distinct().take(8)
    }
    val chipFiles = (mentionedFiles + toolFiles).distinct().take(8)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 12.dp),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            shape = if (isUser) {
                RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
            } else {
                RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
            },
            color = if (isUser) UserBubbleColor else AssistantBubbleColor,
            tonalElevation = 2.dp,
            modifier = Modifier
                .widthIn(max = 340.dp)
                .pointerInput(isUser, message.content) {
                    if (isUser) detectTapGestures(onDoubleTap = { onResendPrompt(message.content) })
                }
        ) {
            Column(modifier = Modifier.padding(12.dp)) {

                // 1. Dedicated Agent Terminal & Execution Logs View (Above text reply)
                if (!isUser && message.actionSummary.isNotBlank()) {
                    AgentTerminalBlock(
                        actionSummaryText = message.actionSummary,
                        isExpanded = message.isActionExpanded,
                        onToggle = { onToggleAction(message.id) },
                        onCopyLogs = onCopyCode,
                        onFileClick = onFileClick
                    )
                }

                // 2. Collapsible Thinking Block (if present)
                if (!isUser && message.thinking.isNotBlank()) {
                    ThinkingBlock(
                        thinkingText = message.thinking,
                        isExpanded = message.isThinkingExpanded,
                        onToggle = { onToggleThinking(message.id) }
                    )
                }

                // 3. Main Message Content
                if (isUser) {
                    SelectionContainer {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White
                        )
                    }
                    Row {
                        TextButton(
                            onClick = { onEditClick(message.content) },
                            modifier = Modifier.padding(top = 2.dp)
                        ) { Text(stringResource(R.string.chip_edit), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f)) }
                        TextButton(
                            onClick = { onDeleteClick(message.id) },
                            modifier = Modifier.padding(top = 2.dp)
                        ) { Text(stringResource(R.string.chip_delete), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f)) }
                    }
                } else {
                    if (message.content.isNotBlank()) {
                        MarkdownCodeMessageView(
                            rawText = message.content,
                            onCopyCode = onCopyCode,
                            onRunCode = onRunCode,
                            terminalTheme = terminalTheme
                        )

                        if (!message.isStreaming && !hasError) {
                            Spacer(modifier = Modifier.size(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AssistChip(onClick = { onCopyClick(message.content) }, label = { Text("⧉ " + stringResource(R.string.chip_copy)) })
                                AssistChip(onClick = { onReviewClick(message.id) }, label = { Text("🔍 " + stringResource(R.string.chip_review)) })
                                AssistChip(onClick = onRegenerateClick, label = { Text("↻ " + stringResource(R.string.chip_regenerate)) })
                            }
                        }

                        // 4. Retry Chip on Error
                        if ((hasError || message.content.contains("No PC changes were made", ignoreCase = true)) && !message.isStreaming) {
                            val isTimeout = message.content.contains("[Request Timed Out]", ignoreCase = true)
                            Spacer(modifier = Modifier.size(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AssistChip(
                                    onClick = { onRetryStrongClick(message.id) },
                                    label = { Text("↻ " + stringResource(R.string.chip_retry_cloud), style = MaterialTheme.typography.labelMedium) }
                                )
                                AssistChip(
                                    onClick = onRetryClick,
                                    label = {
                                        Text(
                                            text = if (isTimeout) "⚠️ Restore Prompt (Caution)" else "🔄 Edit & Retry",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                    },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        labelColor = MaterialTheme.colorScheme.onErrorContainer
                                    ),
                                    border = null
                                )
                            }
                        }
                    } else if (message.isStreaming && message.thinking.isBlank() && message.actionSummary.isBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 6.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF39C5BB)
                            )
                            Text(
                                text = "Agent is executing tools on PC...",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontStyle = FontStyle.Italic,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = Color(0xFF89DCEB)
                            )
                        }
                    } else if (message.content.isBlank() && !message.isStreaming) {
                        Column {
                            Text(
                                "This response was interrupted before it was saved.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AssistChip(onClick = onRetryClick, label = { Text("Restore last prompt") })
                                AssistChip(
                                    onClick = { onRetryStrongClick(message.id) },
                                    label = { Text("↻ " + stringResource(R.string.chip_retry_cloud)) }
                                )
                            }
                        }
                    }
                    // Queued prompts wait their turn; the placeholder shows its position.
                    if (!message.isStreaming && message.queuePosition != null) {
                        Text(
                            text = "⏳ " + stringResource(R.string.chip_queued, message.queuePosition ?: 0),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFF9E2AF),
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                    // Single authoritative job progress — rendered even while the content is empty.
                    if (message.isStreaming && message.jobStatus != null) {
                        Text(
                            text = buildString {
                                append(if (message.jobStatus.equals("queued", true)) "Waiting…" else "Working…")
                                message.elapsedSeconds?.let { append(" · ").append(it).append("s") }
                                val event = message.latestEvent?.trim()
                                if (!event.isNullOrBlank() && !event.startsWith("Working", ignoreCase = true)) {
                                    append("\n").append(event)
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF89DCEB),
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                }

                // File chips for paths in the final answer: tap to preview, icon to copy path.
                if (!isUser && !message.isStreaming && chipFiles.isNotEmpty()) {
                    Spacer(modifier = Modifier.size(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        for (path in chipFiles) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.clickable { onFileClick(path) }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "📄 ${path.substringAfterLast('/')}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                                    )
                                    IconButton(
                                        onClick = { onCopyClick(path) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy file path",
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                    if (path.endsWith(".html", true) || path.endsWith(".htm", true)) {
                                        Text(
                                            text = "🌐 Open",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier
                                                .padding(end = 8.dp, top = 4.dp, bottom = 4.dp)
                                                .clickable { onOpenInBrowser(path) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (!isUser && message.delegatedTaskId != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenTask(message.delegatedTaskId.orEmpty()) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("⚙ Working on PC", style = MaterialTheme.typography.labelMedium)
                            Text(
                                message.delegatedSummary ?: message.delegatedTaskId.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text("View details", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(modifier = Modifier.size(6.dp))
                }

                if (!isUser && message.toolResults.isNotEmpty()) {
                    message.toolResults.forEachIndexed { index, result ->
                        ToolResultCard(
                            result = result,
                            key = "${message.id}-$index",
                            onFileClick = onFileClick,
                            onOpenInBrowser = onOpenInBrowser,
                            onUndoWrite = onUndoWrite,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolResultCard(
    result: ToolResultDto,
    key: String,
    onFileClick: (String) -> Unit,
    onOpenInBrowser: (String) -> Unit = {},
    onUndoWrite: (String, String) -> Unit = { _, _ -> },
) {
    var expanded by remember(key) { mutableStateOf(false) }
    var diffExpanded by remember(key) { mutableStateOf(false) }
    val resultText = buildString {
        result.output?.takeIf(String::isNotBlank)?.let { append(it) }
        result.stdout?.takeIf(String::isNotBlank)?.let { if (isNotEmpty()) append('\n'); append("stdout:\n").append(it) }
        result.stderr?.takeIf(String::isNotBlank)?.let { if (isNotEmpty()) append('\n'); append("stderr:\n").append(it) }
        result.error?.takeIf(String::isNotBlank)?.let { if (isNotEmpty()) append('\n'); append("Error: ").append(it) }
        result.data?.takeIf { !it.isJsonNull }?.let { if (isNotEmpty()) append('\n'); append(it.toString()) }
    }.ifBlank { "No output" }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("🔧 ${result.tool}", fontWeight = FontWeight.Bold)
                val status = when {
                    result.timed_out == true -> "Timed out"
                    result.exit_code != null -> "exit ${result.exit_code}"
                    else -> if (expanded) "Hide" else "Details"
                }
                Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Inline unified diff for write_file overwrites (collapsible, color-coded).
            result.diff?.takeIf(String::isNotBlank)?.let { diffText ->
                Text(
                    text = if (diffExpanded) "📝 Diff ▴ Hide" else "📝 Diff ▾ Show",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp).clickable { diffExpanded = !diffExpanded }
                )
                if (diffExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                            .padding(6.dp)
                    ) {
                        diffText.lineSequence().forEach { line ->
                            val lineColor = when {
                                line.startsWith("+") && !line.startsWith("+++") -> Color(0xFF4ADE80)
                                line.startsWith("-") && !line.startsWith("---") -> Color(0xFFF87171)
                                line.startsWith("@@") -> Color(0xFF7DD3FC)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace, fontSize = 11.sp
                                ),
                                color = lineColor
                            )
                        }
                    }
                }
            }
            if (expanded) {
                result.arguments?.takeIf { it.size() > 0 }?.let {
                    Text("Arguments: ${it}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                }
                result.exit_code?.let { Text("Exit code: $it", color = if (it == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium) }
                if (result.timed_out == true) Text("Command timed out", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                SelectionContainer {
                    Text(
                        resultText,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                val previousContent = remember(result.data) {
                    (result.data?.takeIf { it.isJsonObject }?.asJsonObject?.get("previous_content")
                            as? com.google.gson.JsonPrimitive)?.asString?.takeIf(String::isNotBlank)
                }
                val fileTarget = remember(result.data, result.download_url) {
                    val obj = result.data?.takeIf { it.isJsonObject }?.asJsonObject
                    result.download_url
                        ?: obj?.get("path")?.takeIf { it.isJsonPrimitive }?.asString
                        ?: obj?.get("saved_path")?.takeIf { it.isJsonPrimitive }?.asString
                        ?: obj?.get("file_path")?.takeIf { it.isJsonPrimitive }?.asString
                        ?: obj?.get("download_url")?.takeIf { it.isJsonPrimitive }?.asString
                }
                fileTarget?.let { target ->
                    val path = if (target.startsWith("http", true) || target.contains("path=")) runCatching {
                        android.net.Uri.parse(target).getQueryParameter("path")
                    }.getOrNull().orEmpty() else target
                    if (previousContent != null && path.isNotBlank()) {
                        AssistChip(
                            onClick = { onUndoWrite(path, previousContent) },
                            label = { Text("↶ Undo") }
                        )
                    }
                    if (path.isNotBlank()) {
                        AssistChip(onClick = { onFileClick(path) }, label = { Text("Open file") })
                        if (path.endsWith(".html", true) || path.endsWith(".htm", true)) {
                            AssistChip(
                                onClick = { onOpenInBrowser(path) },
                                label = { Text("🌐 Open in browser") }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AgentTerminalBlock(
    actionSummaryText: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onCopyLogs: (String) -> Unit,
    onFileClick: (String) -> Unit = {}
) {
    val scrollState = rememberScrollState()

    // Prefer absolute paths (usable by the file viewer); fall back to bare filenames.
    val detectedFilesList: List<String> = remember(actionSummaryText) {
        val absolute = Regex("(/[A-Za-z0-9_./-]+\\.(?:html|json|java|cpp|txt|kt|py|sh|md|css|js|ts|csv|h))(?![A-Za-z0-9])")
            .findAll(actionSummaryText).map { it.value }.distinct().toList()
        absolute.ifEmpty {
            Regex("([a-zA-Z0-9_-]+\\.(?:html|json|java|cpp|txt|kt|py|sh|md|css|js|ts|csv|h))(?![A-Za-z0-9])")
                .findAll(actionSummaryText).map { it.value }.distinct().toList()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0D1117))
            .padding(8.dp)
    ) {
        // Dark Mode Terminal Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = "Agent Terminal",
                    tint = Color(0xFF7EE787),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$ ubuntu-agent ~",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFF7EE787)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onCopyLogs(actionSummaryText) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Terminal Logs",
                        tint = Color(0xFF8B949E),
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse Terminal" else "Expand Terminal",
                    tint = Color(0xFF8B949E),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState)
                        .padding(top = 8.dp)
                ) {
                    SelectionContainer {
                        Text(
                            text = actionSummaryText.trim(),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            ),
                            color = Color(0xFFA6E3A1)
                        )
                    }
                }

                // Clickable file chips for detected output files
                if (detectedFilesList.isNotEmpty()) {
                    Spacer(modifier = Modifier.size(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        for (file in detectedFilesList) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF21262D),
                                modifier = Modifier.clickable { onFileClick(file) }
                            ) {
                                Text(
                                    text = "📄 ${file.substringAfterLast('/')}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = Color(0xFF58A6FF),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThinkingBlock(
    thinkingText: String,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF14141F))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = "Thinking Process",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Thinking Process",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            SelectionContainer {
                Text(
                    text = thinkingText.trim(),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontStyle = FontStyle.Italic,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    ),
                    color = Color(0xFFA6ADC8),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
