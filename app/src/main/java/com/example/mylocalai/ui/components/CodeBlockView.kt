package com.example.mylocalai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mylocalai.ui.theme.CodeBlockHeader

private sealed class MessageSegment {
    data class TextSegment(val content: String) : MessageSegment()
    data class CodeSegment(val language: String, val code: String) : MessageSegment()
    data class TableSegment(val content: String) : MessageSegment()
}

@Composable
fun MarkdownCodeMessageView(
    rawText: String,
    onCopyCode: (String) -> Unit,
    onRunCode: (String) -> Unit = {},
    terminalTheme: String = "Dracula",
    modifier: Modifier = Modifier
) {
    val segments = parseMarkdownSegments(rawText)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (segment in segments) {
            when (segment) {
                is MessageSegment.TextSegment -> {
                    if (segment.content.isNotBlank()) {
                        SelectionContainer {
                            Text(
                                text = segment.content.trim(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 22.sp
                            )
                        }
                    }
                }
                is MessageSegment.CodeSegment -> {
                    CodeBlockCard(
                        language = segment.language,
                        code = segment.code,
                        onCopyCode = onCopyCode,
                        onRunCode = onRunCode,
                        terminalTheme = terminalTheme
                    )
                }
                is MessageSegment.TableSegment -> {
                    TableBlockCard(
                        tableText = segment.content,
                        onCopyTable = onCopyCode
                    )
                }
            }
        }
    }
}

@Composable
fun CodeBlockCard(
    language: String,
    code: String,
    onCopyCode: (String) -> Unit,
    onRunCode: (String) -> Unit = {},
    terminalTheme: String = "Dracula"
) {
    val scrollState = rememberScrollState()
    val terminalBackground = when (terminalTheme) {
        "Catppuccin" -> Color(0xFF1E1E2E)
        "Classic Matrix Green" -> Color(0xFF000900)
        else -> Color(0xFF282A36)
    }
    val terminalHeader = when (terminalTheme) {
        "Catppuccin" -> Color(0xFF313244)
        "Classic Matrix Green" -> Color(0xFF002B00)
        else -> Color(0xFF44475A)
    }
    val terminalForeground = when (terminalTheme) {
        "Catppuccin" -> Color(0xFFA6E3A1)
        "Classic Matrix Green" -> Color(0xFF00FF41)
        else -> Color(0xFFF8F8F2)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = terminalBackground)
    ) {
        Column {
            // Header Row with Copy Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(terminalHeader)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.ifBlank { "code" }.lowercase(),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = terminalForeground
                )
                IconButton(
                    onClick = { onCopyCode(code) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy to Clipboard",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Monospaced Code Output with Horizontal Scrolling
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .padding(12.dp)
            ) {
                SelectionContainer {
                    Text(
                        text = code.trimEnd(),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        ),
                        color = terminalForeground
                    )
                }
            }
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(onClick = { onCopyCode(code) }, label = { Text("📋 Copy Code") })
                AssistChip(
                    onClick = { onRunCode("Run command: ${code.trim()}") },
                    label = { Text("⚡ Run on PC Immediately") },
                    colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                )
            }
        }
    }
}

@Composable
fun TableBlockCard(
    tableText: String,
    onCopyTable: (String) -> Unit
) {
    val scrollState = rememberScrollState()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CodeBlockHeader)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.TableChart,
                        contentDescription = "Markdown Table",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = " table",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = { onCopyTable(tableText) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Table",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Monospaced Horizontal Scroll View for Tables
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .padding(12.dp)
            ) {
                SelectionContainer {
                    Text(
                        text = tableText.trim(),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun parseMarkdownSegments(input: String): List<MessageSegment> {
    val segments = mutableListOf<MessageSegment>()
    val codeRegex = Regex("```([a-zA-Z0-9_+#-]*)\\n?([\\s\\S]*?)```")

    var lastIndex = 0
    val matches = codeRegex.findAll(input)

    for (match in matches) {
        val startIndex = match.range.first
        val endIndex = match.range.last + 1

        if (startIndex > lastIndex) {
            val textBefore = input.substring(lastIndex, startIndex)
            parseTextAndTables(textBefore, segments)
        }

        val language = match.groupValues.getOrNull(1)?.trim() ?: ""
        val code = match.groupValues.getOrNull(2) ?: ""
        segments.add(MessageSegment.CodeSegment(language = language, code = code))

        lastIndex = endIndex
    }

    if (lastIndex < input.length) {
        val remainingText = input.substring(lastIndex)
        parseTextAndTables(remainingText, segments)
    }

    if (segments.isEmpty() && input.isNotBlank()) {
        segments.add(MessageSegment.TextSegment(input))
    }

    return segments
}

private fun parseTextAndTables(text: String, segments: MutableList<MessageSegment>) {
    val lines = text.split("\n")
    val currentText = StringBuilder()
    val currentTable = StringBuilder()
    var inTable = false

    for (line in lines) {
        val isTableLine = line.trim().startsWith("|") && line.trim().endsWith("|")
        if (isTableLine) {
            if (!inTable) {
                if (currentText.isNotBlank()) {
                    segments.add(MessageSegment.TextSegment(currentText.toString()))
                    currentText.clear()
                }
                inTable = true
            }
            currentTable.append(line).append("\n")
        } else {
            if (inTable) {
                if (currentTable.isNotBlank()) {
                    segments.add(MessageSegment.TableSegment(currentTable.toString()))
                    currentTable.clear()
                }
                inTable = false
            }
            currentText.append(line).append("\n")
        }
    }

    if (inTable && currentTable.isNotBlank()) {
        segments.add(MessageSegment.TableSegment(currentTable.toString()))
    } else if (currentText.isNotBlank()) {
        segments.add(MessageSegment.TextSegment(currentText.toString()))
    }
}
