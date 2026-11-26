package com.example.mylocalai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.mylocalai.model.SoftwareTaskDiff
import com.example.mylocalai.model.SoftwareTaskEvent
import com.example.mylocalai.model.SoftwareTaskRequest
import com.example.mylocalai.model.SoftwareTaskSummary
import org.json.JSONObject

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SoftwareTasksDialog(
    tasks: List<SoftwareTaskSummary>,
    selectedTaskId: String?,
    events: List<SoftwareTaskEvent>,
    models: List<String>,
    selectedModel: String,
    loading: Boolean,
    restoring: Boolean,
    creating: Boolean,
    error: String?,
    pendingCreate: Boolean,
    diff: SoftwareTaskDiff?,
    diffLoading: Boolean,
    deleteConfirmation: String?,
    onSelectModel: (String) -> Unit,
    onCreate: (SoftwareTaskRequest) -> Unit,
    onRetryCreate: () -> Unit,
    onSelectTask: (String) -> Unit,
    onRefreshTask: (String) -> Unit,
    onResume: (String, String, Int, Int) -> Unit,
    onCancelTask: (String) -> Unit,
    onLoadDiff: (String) -> Unit,
    onCloseDiff: () -> Unit,
    onExportDiff: (SoftwareTaskDiff) -> Unit,
    onConfirmDeleteWorktree: (String) -> Unit,
    onDismissDeleteWorktree: () -> Unit,
    onDeleteWorktree: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var objective by remember { mutableStateOf("") }
    var workspace by remember { mutableStateOf("") }
    var criteria by remember { mutableStateOf("") }
    var verifyCommands by remember { mutableStateOf("") }
    var selectedModelName by remember(selectedModel) { mutableStateOf(selectedModel) }
    var maxSteps by remember { mutableStateOf("24") }
    var maxSeconds by remember { mutableStateOf("21600") }
    var worktree by remember { mutableStateOf(true) }
    var resumeMessage by remember { mutableStateOf("") }
    var additionalSteps by remember { mutableStateOf("") }
    var additionalSeconds by remember { mutableStateOf("") }
    var showBudgetControls by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val task = tasks.firstOrNull { it.taskId == selectedTaskId }
    val usesResearch = events.any { event ->
        event.eventType.contains("research", ignoreCase = true) ||
            event.message.contains("research", ignoreCase = true) ||
            event.rawJson.contains("research", ignoreCase = true)
    }
    val researchQueries = events.mapNotNull { event ->
        runCatching { JSONObject(event.rawJson).optString("query").takeIf(String::isNotBlank) }.getOrNull()
    }.distinct()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.94f).padding(horizontal = 20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Software tasks", style = MaterialTheme.typography.headlineSmall)
                    Text("Persistent, reviewable project work", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onDismiss) { Text("Done") }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (restoring) Text("Restoring saved task state before allowing a new task…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                if (pendingCreate) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Task creation outcome is unknown", style = MaterialTheme.typography.titleSmall)
                            Text("Do not start a new task. Recover by repeating the same request with its saved Idempotency-Key.", style = MaterialTheme.typography.bodySmall)
                            AssistChip(onClick = onRetryCreate, enabled = !creating && !restoring, label = { Text(if (creating) "Recovering…" else if (restoring) "Restoring saved task…" else "Recover using same key") })
                        }
                    }
                } else {
                    Text("Create task", style = MaterialTheme.typography.titleMedium)
                    Text("Agent Bridge task/job coordination must run with a single server worker. Completion is only reported after the configured verification commands pass; review the resulting diff.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                    OutlinedTextField(objective, { objective = it }, label = { Text("Objective") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4)
                    OutlinedTextField(workspace, { workspace = it }, label = { Text("Absolute Ubuntu workspace path") }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("/home/snow/project") })
                    OutlinedTextField(criteria, { criteria = it }, label = { Text("Acceptance criteria · one per line") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4)
                    OutlinedTextField(verifyCommands, { verifyCommands = it }, label = { Text("Verification commands · one per line") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4)
                    Text("Choose checks appropriate for this project; completion depends on these commands passing.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val suggestions = when {
                            workspace.contains("android", true) || workspace.contains("gradle", true) -> listOf("./gradlew :app:compileDebugKotlin", "./gradlew test")
                            workspace.contains("website", true) || workspace.contains("site", true) || workspace.contains("html", true) -> listOf("test -f index.html && test -f style.css && test -f script.js", "grep -qi '<title>' index.html")
                            workspace.contains("node", true) || workspace.contains("web", true) -> listOf("npm test", "npm run build")
                            workspace.contains("python", true) -> listOf("python3 -m compileall .", "python3 -m pytest -q")
                            else -> listOf("./gradlew test", "npm test", "python3 -m pytest -q")
                        }
                        suggestions.forEach { suggestion ->
                            AssistChip(onClick = { verifyCommands = (verifyCommands.lines().filter(String::isNotBlank) + suggestion).distinct().joinToString("\n") }, label = { Text(suggestion) })
                        }
                    }
                    OutlinedTextField(selectedModelName, { selectedModelName = it }, label = { Text("Model from /api/tags") }, modifier = Modifier.fillMaxWidth(), singleLine = true, readOnly = models.isNotEmpty())
                    if (models.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        models.forEach { model -> AssistChip(onClick = { selectedModelName = model; onSelectModel(model) }, label = { Text(model) }) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(maxSteps, { maxSteps = it }, label = { Text("Max steps") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(maxSeconds, { maxSeconds = it }, label = { Text("Max seconds") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    AssistChip(onClick = { worktree = !worktree }, label = { Text(if (worktree) "✓ Use isolated Git worktree" else "Use isolated Git worktree") })
                    if (worktree) Text("A worktree is a separate copy from Git HEAD; uncommitted changes in the original workspace are not included.", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
                    AssistChip(
                        onClick = {
                            val criteriaLines = criteria.lines().map(String::trim).filter(String::isNotEmpty)
                            val commands = verifyCommands.lines().map(String::trim).filter(String::isNotEmpty)
                            if (objective.isBlank() || !workspace.startsWith("/") || criteriaLines.isEmpty() || commands.isEmpty() || selectedModelName.isBlank()) return@AssistChip
                            onCreate(SoftwareTaskRequest(objective.trim(), workspace.trim(), criteriaLines, commands, selectedModelName.trim(), worktree, maxSteps.toIntOrNull()?.coerceIn(1, 200) ?: 24, maxSeconds.toIntOrNull()?.coerceIn(60, 86400) ?: 21600))
                        },
                        enabled = !creating && !restoring && objective.isNotBlank() && workspace.startsWith("/") && criteria.isNotBlank() && verifyCommands.isNotBlank() && selectedModelName.isNotBlank(),
                        label = { Text(if (creating) "Creating task…" else if (restoring) "Restoring saved task…" else "Create persistent task") },
                    )
                }

                HorizontalDivider()
                Text("Tracked tasks", style = MaterialTheme.typography.titleMedium)
                if (loading && tasks.isEmpty()) CircularProgressIndicator()
                if (tasks.isEmpty() && !loading) Text("No tracked software tasks yet.", style = MaterialTheme.typography.bodySmall)
                tasks.forEach { item ->
                    Card(onClick = { onSelectTask(item.taskId) }, colors = CardDefaults.cardColors(containerColor = if (item.taskId == selectedTaskId) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${item.status.uppercase()} · ${item.taskId}", style = MaterialTheme.typography.labelLarge)
                            Text(item.objective, style = MaterialTheme.typography.bodyMedium)
                            if (item.phase.isNotBlank()) Text("Phase: ${item.phase}", style = MaterialTheme.typography.bodySmall)
                            if (item.stepsDone != null || item.stepsTotal != null) Text("Steps: ${item.stepsDone ?: 0} / ${item.stepsTotal ?: "?"}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                task?.let { selected ->
                    HorizontalDivider()
                    Text("Task details", style = MaterialTheme.typography.titleMedium)
                    Text("Status: ${selected.status} · Phase: ${selected.phase.ifBlank { "—" }}", style = MaterialTheme.typography.bodyMedium)
                    Text("Steps: ${selected.stepsDone ?: 0} / ${selected.stepsTotal ?: "?"}", style = MaterialTheme.typography.bodySmall)
                    if (usesResearch) {
                        Text(
                            text = buildString {
                                append("🔍 Research tool used")
                                researchQueries.firstOrNull()?.let { query -> append(" — ").append(query.take(120)) }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    selected.activeJobId?.let { Text("Active job: $it", style = MaterialTheme.typography.bodySmall) }
                    selected.workspacePath?.let { Text("Workspace: $it", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
                    selected.worktreePath?.let { Text("Worktree: $it", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
                    selected.plan?.let { TaskTextCard("Saved plan", it) }
                    selected.error?.let { TaskTextCard("Task error", it, true) }
                    selected.finalReport?.let { TaskTextCard("Final report · server status ${selected.status}", it) }
                    TaskTextCard("Task status payload", selected.rawJson)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AssistChip(onClick = { onRefreshTask(selected.taskId) }, enabled = !loading, label = { Text("Refresh") })
                        if (selected.worktreePath != null) AssistChip(onClick = { onLoadDiff(selected.taskId) }, enabled = !diffLoading, label = { Text(if (diffLoading) "Loading diff…" else "Review diff") })
                        if (selected.status.lowercase() in setOf("queued", "running")) AssistChip(onClick = { onCancelTask(selected.taskId) }, enabled = !loading, label = { Text("Cancel task") })
                    }
                    if (selected.status.lowercase() in setOf("paused", "needs_input")) {
                        Text("Review the plan, recent events, and workspace before resuming. The app will not resume automatically.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                        OutlinedTextField(resumeMessage, { resumeMessage = it }, label = { Text(if (selected.status.equals("needs_input", true)) "Your response" else "Reconciliation message") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4)
                        AssistChip(onClick = { onResume(selected.taskId, resumeMessage, 0, 0) }, enabled = !loading && resumeMessage.isNotBlank(), label = { Text("Review complete · Resume") })
                        AssistChip(onClick = { showBudgetControls = !showBudgetControls }, label = { Text(if (showBudgetControls) "Hide budget increase" else "Explicitly increase budget") })
                        if (showBudgetControls) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(additionalSteps, { additionalSteps = it }, label = { Text("Additional steps") }, modifier = Modifier.weight(1f), singleLine = true)
                                OutlinedTextField(additionalSeconds, { additionalSeconds = it }, label = { Text("Additional seconds") }, modifier = Modifier.weight(1f), singleLine = true)
                            }
                            AssistChip(
                                onClick = { onResume(selected.taskId, resumeMessage, additionalSteps.toIntOrNull()?.coerceAtLeast(0) ?: 0, additionalSeconds.toIntOrNull()?.coerceAtLeast(0) ?: 0) },
                                enabled = !loading && resumeMessage.isNotBlank() && ((additionalSteps.toIntOrNull() ?: 0) > 0 || (additionalSeconds.toIntOrNull() ?: 0) > 0),
                                label = { Text("Increase budget & resume") }
                            )
                        }
                    }
                    Text("Events", style = MaterialTheme.typography.titleSmall)
                    if (events.isEmpty()) Text("No events fetched yet.", style = MaterialTheme.typography.bodySmall)
                    events.forEach { event ->
                        TaskTextCard("#${event.id} · ${event.eventType}", event.rawJson.ifBlank { event.message })
                    }
                }
            }
        }
    }

    diff?.let { value ->
        var diffReviewed by remember(value) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = onCloseDiff,
            title = { Text("Task diff") },
            text = {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Base commit: ${value.baseCommit}", style = MaterialTheme.typography.bodySmall)
                    Text("Worktree: ${value.worktreePath}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    if (value.truncated) Text("Warning: the server truncated this diff. Some changes may be omitted.", color = MaterialTheme.colorScheme.error)
                    if (value.untrackedFiles.isNotEmpty()) Text("Some untracked files may be omitted from the patch; review these files separately:\n${value.untrackedFiles.joinToString("\n")}", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
                    Text(value.diff.ifBlank { "No tracked diff content was returned." }, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Column {
                    TextButton(onClick = { onExportDiff(value) }, modifier = Modifier.fillMaxWidth()) { Text("Export diff") }
                    if (!diffReviewed) TextButton(onClick = { diffReviewed = true }, modifier = Modifier.fillMaxWidth()) { Text("I reviewed this diff") }
                    else TextButton(onClick = { onConfirmDeleteWorktree(selectedTaskId.orEmpty()) }, modifier = Modifier.fillMaxWidth()) { Text("Remove worktree") }
                    TextButton(onClick = onCloseDiff, modifier = Modifier.fillMaxWidth()) { Text("Close") }
                }
            },
        )
    }
    deleteConfirmation?.let { taskId ->
        AlertDialog(
            onDismissRequest = onDismissDeleteWorktree,
            title = { Text("Confirm worktree removal") },
            text = { Text("Remove the uncommitted task changes for $taskId?") },
            confirmButton = { TextButton(onClick = { onDeleteWorktree(taskId) }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = onDismissDeleteWorktree) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TaskTextCard(title: String, body: String, isError: Boolean = false) {
    Card(colors = CardDefaults.cardColors(containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(body, style = MaterialTheme.typography.bodySmall, fontFamily = if (body.length > 120) FontFamily.Monospace else FontFamily.Default)
        }
    }
}
