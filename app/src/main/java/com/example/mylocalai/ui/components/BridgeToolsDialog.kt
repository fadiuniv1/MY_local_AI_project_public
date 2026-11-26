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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.mylocalai.model.DesktopRequest

private data class PendingAdminAction(
    val title: String,
    val detail: String,
    val action: () -> Unit,
)

@OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)
@Composable
fun BridgeToolsDialog(
    busy: Boolean,
    result: String?,
    onDirectCommand: (String) -> Unit,
    onBackgroundCommand: (String) -> Unit,
    onSearch: (String) -> Unit,
    onProcesses: () -> Unit,
    onService: (String, String) -> Unit,
    onPackage: (String, String) -> Unit,
    onSchedule: (String, String, String) -> Unit,
    onApps: (String) -> Unit,
    onLaunchApp: (String) -> Unit,
    onDesktop: (DesktopRequest) -> Unit,
    onDesktopDiagnostics: () -> Unit,
    onHealth: () -> Unit,
    onLogs: () -> Unit,
    onTags: () -> Unit,
    onOpenJobs: () -> Unit,
    onSoftwareTasks: () -> Unit,
    onDismiss: () -> Unit,
) {
    var command by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var service by remember { mutableStateOf("ollama.service") }
    var packageName by remember { mutableStateOf("firefox") }
    var schedule by remember { mutableStateOf("daily") }
    var scheduledCommand by remember { mutableStateOf("df -h /") }
    var scheduleUnit by remember { mutableStateOf("") }
    var appId by remember { mutableStateOf("firefox.desktop") }
    var desktopX by remember { mutableStateOf("400") }
    var desktopY by remember { mutableStateOf("300") }
    var pendingAdminAction by remember { mutableStateOf<PendingAdminAction?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = LocalFocusManager.current
    fun dispatch(action: () -> Unit) {
        focusManager.clearFocus()
        action()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(horizontal = 20.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Ubuntu workstation", style = MaterialTheme.typography.headlineSmall)
                    Text("Remote tools · actions run on your PC", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onDismiss) { Text("Done") }
            }
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ToolSection("Command runner", "Run a short command now or monitor longer work as a background job.") {
                    OutlinedTextField(command, { command = it }, label = { Text("Bash command") }, modifier = Modifier.fillMaxWidth(), minLines = 1, maxLines = 3)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        AssistChip(onClick = { dispatch { onDirectCommand(command) } }, label = { Text("▶ Run now") }, enabled = command.isNotBlank() && !busy)
                        AssistChip(onClick = { dispatch { onBackgroundCommand(command) } }, label = { Text("↗ Start job") }, enabled = command.isNotBlank() && !busy)
                        AssistChip(onClick = { dispatch(onOpenJobs) }, label = { Text("Jobs") })
                        AssistChip(onClick = { dispatch(onSoftwareTasks) }, label = { Text("Software tasks") })
                    }
                }

                ToolSection("Search & diagnostics", "Search the web or inspect the current Ubuntu system state.") {
                    OutlinedTextField(query, { query = it }, label = { Text("Search or app name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        AssistChip(onClick = { dispatch { onSearch(query) } }, label = { Text("Web search") }, enabled = query.isNotBlank() && !busy)
                        AssistChip(onClick = { dispatch(onProcesses) }, label = { Text("Top processes") }, enabled = !busy)
                        AssistChip(onClick = { dispatch(onHealth) }, label = { Text("API health") }, enabled = !busy)
                        AssistChip(onClick = { dispatch(onLogs) }, label = { Text("Server logs") }, enabled = !busy)
                        AssistChip(onClick = { dispatch(onTags) }, label = { Text("Installed models") }, enabled = !busy)
                    }
                }

                ToolSection("System services", "Service operations use the Ubuntu service account’s permissions.") {
                    OutlinedTextField(service, { service = it }, label = { Text("Service unit") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("status", "start", "stop", "restart").forEach { action ->
                            AssistChip(onClick = {
                                focusManager.clearFocus()
                                val selectedService = service
                                if (action == "stop" || action == "restart") {
                                    pendingAdminAction = PendingAdminAction(
                                        title = "${action.replaceFirstChar(Char::uppercase)} service?",
                                        detail = "This will run systemctl $action $selectedService on Ubuntu.",
                                        action = { onService(selectedService, action) },
                                    )
                                } else onService(selectedService, action)
                            }, label = { Text(action.replaceFirstChar(Char::uppercase)) }, enabled = service.isNotBlank() && !busy)
                        }
                    }
                }

                ToolSection("Packages", "Installs and upgrades may take time; their job status appears in Background jobs.") {
                    OutlinedTextField(packageName, { packageName = it }, label = { Text("Package name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("search", "show", "install", "remove").forEach { action ->
                            AssistChip(onClick = {
                                focusManager.clearFocus()
                                val selectedPackage = packageName
                                if (action == "install" || action == "remove") {
                                    pendingAdminAction = PendingAdminAction(
                                        title = "${action.replaceFirstChar(Char::uppercase)} $selectedPackage?",
                                        detail = "This changes installed software on the Ubuntu workstation.",
                                        action = { onPackage(action, selectedPackage) },
                                    )
                                } else onPackage(action, selectedPackage)
                            }, label = { Text(action.replaceFirstChar(Char::uppercase)) }, enabled = packageName.isNotBlank() && !busy)
                        }
                        listOf("update-index", "upgrade", "list-upgradable").forEach { action ->
                            AssistChip(onClick = {
                                focusManager.clearFocus()
                                if (action == "upgrade") {
                                    pendingAdminAction = PendingAdminAction(
                                        title = "Upgrade packages?",
                                        detail = "This may install or update multiple packages on Ubuntu.",
                                        action = { onPackage(action, "") },
                                    )
                                } else onPackage(action, "")
                            }, label = { Text(action.replace('-', ' ').replaceFirstChar(Char::uppercase)) }, enabled = !busy)
                        }
                    }
                }

                ToolSection("Scheduled tasks", "Create a systemd timer, list timers, or cancel a returned unit.") {
                    OutlinedTextField(schedule, { schedule = it }, label = { Text("Schedule (for example, daily)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(scheduledCommand, { scheduledCommand = it }, label = { Text("Command") }, modifier = Modifier.fillMaxWidth(), minLines = 1, maxLines = 2)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { dispatch { onSchedule("create", schedule, scheduledCommand) } }, label = { Text("Create timer") }, enabled = !busy && schedule.isNotBlank() && scheduledCommand.isNotBlank())
                        AssistChip(onClick = { dispatch { onSchedule("list", "", "") } }, label = { Text("List timers") }, enabled = !busy)
                    }
                    OutlinedTextField(scheduleUnit, { scheduleUnit = it }, label = { Text("Unit to cancel (from result)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    AssistChip(onClick = { dispatch { onSchedule("cancel", "", scheduleUnit) } }, label = { Text("Cancel timer") }, enabled = !busy && scheduleUnit.isNotBlank())
                }

                ToolSection("Desktop session", "Desktop controls require an active graphical session; input automation requires X11/xdotool.") {
                    OutlinedTextField(appId, { appId = it }, label = { Text("Application ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        AssistChip(onClick = { dispatch { onApps(query) } }, label = { Text("Find apps") }, enabled = !busy)
                        AssistChip(onClick = { dispatch { onLaunchApp(appId) } }, label = { Text("Launch app") }, enabled = appId.isNotBlank() && !busy)
                        AssistChip(onClick = { dispatch { onDesktop(DesktopRequest("screenshot")) } }, label = { Text("Screenshot") }, enabled = !busy)
                        AssistChip(onClick = { dispatch(onDesktopDiagnostics) }, label = { Text("Diagnostics") }, enabled = !busy)
                        AssistChip(onClick = { dispatch { onDesktop(DesktopRequest("type", text = command)) } }, label = { Text("Type command") }, enabled = command.isNotBlank() && !busy)
                        AssistChip(onClick = { dispatch { onDesktop(DesktopRequest("key", key = "ctrl+l")) } }, label = { Text("Send Ctrl+L") }, enabled = !busy)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(desktopX, { desktopX = it }, label = { Text("X") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(desktopY, { desktopY = it }, label = { Text("Y") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    AssistChip(onClick = { dispatch { onDesktop(DesktopRequest("click", x = desktopX.toIntOrNull(), y = desktopY.toIntOrNull())) } }, label = { Text("Click coordinates") }, enabled = !busy && desktopX.toIntOrNull() != null && desktopY.toIntOrNull() != null)
                }

                if (busy) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator()
                    Text("Waiting for Ubuntu…", style = MaterialTheme.typography.bodyMedium)
                }
                result?.let { ToolSection("Latest response", "Tap and hold to select or copy output.") {
                    SelectionContainer {
                        Text(it, modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState()), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                } }
            }
        }
    }

    pendingAdminAction?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingAdminAction = null },
            title = { Text(pending.title) },
            text = { Text(pending.detail) },
            confirmButton = {
                TextButton(onClick = {
                    pendingAdminAction = null
                    pending.action()
                }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { pendingAdminAction = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ToolSection(title: String, description: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}
