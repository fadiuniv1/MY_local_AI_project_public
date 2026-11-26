package com.example.mylocalai

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.mylocalai.model.ConnectionMode
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.core.view.WindowCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mylocalai.ui.ChatViewModel
import com.example.mylocalai.ui.components.BottomInputBar
import com.example.mylocalai.ui.components.defaultMacroShortcuts
import com.example.mylocalai.ui.components.ChatMessageItem
import com.example.mylocalai.ui.components.ChatQueueDialog
import com.example.mylocalai.ui.components.PlanDialog
import com.example.mylocalai.ui.components.ReviewDialog
import com.example.mylocalai.ui.components.FileViewerDialog
import com.example.mylocalai.ui.components.ServerLogsDialog
import com.example.mylocalai.ui.components.SettingsDialog
import com.example.mylocalai.ui.components.SystemStatsDialog
import com.example.mylocalai.ui.components.TopAppBarComponent
import com.example.mylocalai.ui.components.RemoteFileExplorerDialog
import com.example.mylocalai.ui.components.BridgeToolsDialog
import com.example.mylocalai.ui.components.JobsDialog
import com.example.mylocalai.ui.components.BridgeAuditHistoryDialog
import com.example.mylocalai.ui.components.AgentNotesDialog
import com.example.mylocalai.ui.components.HealthDiagnosticsDialog
import com.example.mylocalai.ui.components.SoftwareTasksDialog
import com.example.mylocalai.model.DesktopRequest
import com.example.mylocalai.model.SoftwareTaskDiff
import com.example.mylocalai.notification.AppVisibility
import com.example.mylocalai.ui.theme.MYLocalAITheme
import androidx.compose.ui.res.stringResource
import com.example.mylocalai.R
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context?) {
        val base = newBase ?: return super.attachBaseContext(null)
        super.attachBaseContext(com.example.mylocalai.util.LocaleHelper.wrap(
            base, com.example.mylocalai.util.LocaleHelper.getLanguage(base)
        ))
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.isForeground = true
    }

    override fun onStop() {
        AppVisibility.isForeground = false
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()
        setContent {
            MYLocalAITheme {
                MainScreen()
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MainScreen(viewModel: ChatViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var pendingCommand by remember { mutableStateOf<String?>(null) }
    var pendingNotificationAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingTaskDiffExport by remember { mutableStateOf<SoftwareTaskDiff?>(null) }
    var showUbuntuMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val activity = context as? MainActivity
        val notificationSessionId = activity?.intent?.getStringExtra("notification_session_id")
        if (!notificationSessionId.isNullOrBlank()) {
            viewModel.switchSession(notificationSessionId)
            activity.intent?.removeExtra("notification_session_id")
        }
        if (activity?.intent?.getStringExtra("completed_job_id") != null) {
            viewModel.openJobs()
            activity.intent?.removeExtra("completed_job_id")
        }
        val softwareTaskId = activity?.intent?.getStringExtra("software_task_id")
        if (!softwareTaskId.isNullOrBlank()) {
            viewModel.openSoftwareTasks()
            viewModel.selectSoftwareTask(softwareTaskId)
            activity.intent?.removeExtra("software_task_id")
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val action = pendingNotificationAction
        pendingNotificationAction = null
        if (!granted) Toast.makeText(context, "Notifications are off; requests still run, but completion alerts are disabled.", Toast.LENGTH_LONG).show()
        action?.invoke()
    }

    fun withCompletionNotifications(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingNotificationAction = action
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            action()
        }
    }

    fun submitPrompt(prompt: String) {
        if (prompt.trimStart().startsWith("Run command:", ignoreCase = true)) {
            pendingCommand = prompt
        } else {
            withCompletionNotifications {
                viewModel.updateInputText(prompt)
                viewModel.sendMessage()
            }
        }
    }

    // System File Picker Launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.uploadFile(it, context)
        }
    }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.addChatImage(it, context) } }
    val remoteUploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.uploadToRemoteFolder(it, context) } }
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                val current = viewModel.uiState.value.inputText
                viewModel.updateInputText(if (current.isBlank()) spoken else "$current $spoken")
            }
        }
    }
    val saveServerLogsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val logs = viewModel.uiState.value.serverLogs
        if (uri != null && logs != null) viewModel.saveServerLogs(uri, logs)
    }
    val saveTaskDiffLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/x-diff")
    ) { uri ->
        val diff = pendingTaskDiffExport
        if (uri != null && diff != null) viewModel.saveSoftwareTaskDiff(uri, diff)
        pendingTaskDiffExport = null
    }

    // Toast copy handler
    val handleCopyCode = { code: String ->
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Copied Content", code)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    // Auto-scroll to bottom as tokens/logs stream or new messages arrive
    LaunchedEffect(
        uiState.messages.size,
        uiState.messages.lastOrNull()?.content?.length,
        uiState.messages.lastOrNull()?.thinking?.length,
        uiState.messages.lastOrNull()?.actionSummary?.length
    ) {
        if (uiState.messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(uiState.messages.size - 1)
            }
        }
    }

    // Show error snackbar if error occurs
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            val result = snackbarHostState.showSnackbar(msg, actionLabel = if (uiState.canRetryUpload) "Retry upload" else null)
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed && uiState.canRetryUpload) viewModel.retryLastUpload()
            viewModel.clearErrorMessage()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(modifier = Modifier.fillMaxHeight()) {
                    TextButton(
                        onClick = {
                            viewModel.createNewSession()
                            coroutineScope.launch { drawerState.close() }
                        },
                        modifier = Modifier.fillMaxWidth().padding(12.dp)
                    ) { Text("＋ New Chat") }
                    TextButton(
                        onClick = {
                            showUbuntuMenu = true
                            coroutineScope.launch { drawerState.close() }
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                    ) { Text("🖥️ Ubuntu PC · files, tools, jobs, health") }
                    OutlinedTextField(
                        value = uiState.sessionSearchQuery,
                        onValueChange = viewModel::updateSessionSearch,
                        label = { Text("Search chats and messages") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                    )
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(items = uiState.sessions, key = { it.sessionId }) { session ->
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value == SwipeToDismissBoxValue.EndToStart) {
                                        viewModel.deleteSession(session.sessionId)
                                        true
                                    } else false
                                }
                            )
                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = false,
                                backgroundContent = {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        contentAlignment = androidx.compose.ui.Alignment.CenterEnd
                                    ) { Text("Delete", color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
                                }
                            ) {
                                NavigationDrawerItem(
                                    label = {
                                        Column {
                                            Text(session.title, maxLines = 1)
                                            uiState.sessionTaskInfo[session.sessionId]?.let { info ->
                                                if (info.goal.isNotBlank()) {
                                                    Text(
                                                        text = "↳ ${info.goal} · ${info.status}",
                                                        maxLines = 1,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                    )
                                                }
                                            }
                                            Text(
                                                text = if (session.preview.isBlank()) "No messages yet" else "${session.messageCount} messages · ${session.preview.replace('\n', ' ')}",
                                                maxLines = 2,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    selected = session.sessionId == uiState.sessionId,
                                    onClick = {
                                        viewModel.switchSession(session.sessionId)
                                        coroutineScope.launch { drawerState.close() }
                                    },
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) {
    Scaffold(
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            TopAppBarComponent(
                isConnected = uiState.isConnected,
                connectionMode = uiState.connectionMode,
                availableModels = uiState.availableModels,
                selectedModel = uiState.selectedModel,
                isFetchingModels = uiState.isFetchingModels,
                queuedCount = uiState.queuedCount,
                onQueueClick = { viewModel.openQueueDialog() },
                currentTaskGoal = uiState.sessionTaskInfo[uiState.sessionId]?.goal,
                onModeToggle = { viewModel.setConnectionMode(it) },
                onModelSelected = { viewModel.selectModel(it) },
                onNewChatClick = {
                    viewModel.createNewSession()
                },
                onStatsClick = { viewModel.openSystemStats() },
                onLogsClick = { viewModel.openServerLogs() },
                onSettingsClick = { viewModel.openSettings() },
                onRetryConnection = { viewModel.refreshServerCapabilities() },
                onRefreshModels = { viewModel.refreshServerCapabilities() },
                onExportChat = {
                    val text = uiState.messages.joinToString("\n\n") { msg ->
                        "${msg.role.uppercase()}: ${msg.content}"
                    }.ifBlank { "Empty chat" }
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    runCatching {
                        context.startActivity(Intent.createChooser(shareIntent, "Export chat"))
                    }.onFailure {
                        Toast.makeText(context, "No app available to share chat", Toast.LENGTH_SHORT).show()
                    }
                },
                onNavigationClick = { coroutineScope.launch { drawerState.open() } },
                onClearConversationClick = { viewModel.requestClearCurrentConversation() },
                onClearAllChatsClick = { viewModel.requestClearAllChats() }
            )
        },
        bottomBar = {
            BottomInputBar(
                inputText = uiState.inputText,
                isStreaming = uiState.isStreaming,
                isUploading = uiState.isUploading,
                uploadProgress = uiState.uploadProgress,
                onInputTextChange = { viewModel.updateInputText(it) },
                onSendClick = {
                    focusManager.clearFocus()
                    submitPrompt(uiState.inputText)
                },
                onStopClick = { viewModel.stopStreaming() },
                onMacroClick = { prompt ->
                    focusManager.clearFocus()
                    // Macros that run commands still require the same approval as typed commands.
                    if (prompt.trimStart().startsWith("Run command:", ignoreCase = true)) {
                        pendingCommand = prompt
                    } else {
                        submitPrompt(prompt)
                    }
                },
                onAttachFileClick = { filePickerLauncher.launch("*/*") },
                onAttachImageClick = { imagePickerLauncher.launch("image/*") },
                pendingImageCount = uiState.pendingChatImages.size,
                onClearImages = viewModel::clearChatImages,
                onPlanClick = {
                    focusManager.clearFocus()
                    viewModel.requestPlan(uiState.inputText)
                },
                onVoiceClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak a command")
                    }
                    runCatching { speechLauncher.launch(intent) }
                        .onFailure { Toast.makeText(context, "Speech recognition is unavailable", Toast.LENGTH_SHORT).show() }
                },
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .imePadding()
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.isAwaitingNetwork) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.reconnect_banner),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.refreshServerCapabilities() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
            if (uiState.isDownloadingRemoteFile ||
                uiState.fileDownloadMessage != null ||
                uiState.fileDownloadError != null
            ) {
                val isError = uiState.fileDownloadError != null
                Surface(
                    color = if (isError) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !uiState.isDownloadingRemoteFile) {
                            viewModel.clearFileDownloadStatus()
                        }
                ) {
                    Text(
                        text = when {
                            uiState.isDownloadingRemoteFile -> "↓ Downloading ${uiState.downloadingFileName ?: "file"}…"
                            isError -> "⚠ ${uiState.fileDownloadError}"
                            else -> "✓ ${uiState.fileDownloadMessage}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isError) MaterialTheme.colorScheme.onErrorContainer
                                else MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            val health = uiState.serverHealthMetrics
            val lowMemory = health?.availableMemoryBytes?.let { it < 1_073_741_824L } == true
            val stuck = (health?.staleTaskHeartbeats ?: 0) > 0
            if (lowMemory || stuck) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.openSystemStats() }
                ) {
                    Text(
                        text = buildString {
                            append("⚠ ")
                            if (lowMemory) append("Low RAM on Ubuntu PC")
                            if (lowMemory && stuck) append(" · ")
                            if (stuck) append("Stuck worker detected")
                            append(" · tap for details")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            if (uiState.messages.isEmpty()) {
                ChatEmptyState(
                    connectionMode = uiState.connectionMode,
                    isConnected = uiState.isConnected,
                    onSuggestionClick = { prompt ->
                        viewModel.updateInputText(prompt)
                    },
                    onOpenFilesClick = { viewModel.openRemoteFiles() },
                    onOpenStatsClick = { viewModel.openSystemStats() },
                    onContinueTask = { viewModel.continueLastTask() },
                    canContinueTask = uiState.sessionTaskInfo[uiState.sessionId]?.goal?.isNotBlank() == true
                )
            } else {
                // Collapse identical prompts that produced only a no-op reply into one bubble.
                val displayMessages = remember(uiState.messages) {
                    val out = mutableListOf<com.example.mylocalai.model.ChatMessage>()
                    var repeated = 0
                    var lastUserText = ""
                    for (message in uiState.messages) {
                        if (message.role == "user") {
                            if (message.content.trim() == lastUserText && lastUserText.isNotBlank()) {
                                repeated += 1
                                continue
                            }
                            repeated = 0
                            lastUserText = message.content.trim()
                            out.add(message)
                        } else {
                            val noop = message.content.contains("No PC changes were made", ignoreCase = true)
                            if (repeated > 0 && noop) {
                                val index = out.lastIndex
                                if (index >= 0 && out[index].role == "assistant") {
                                    out[index] = out[index].copy(
                                        content = out[index].content +
                                            "\n\n[Repeated $repeated more time(s) without changes]"
                                    )
                                }
                                repeated = 0
                                continue
                            }
                            out.add(message)
                        }
                    }
                    out
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(
                        items = displayMessages,
                        key = { message -> message.id }
                    ) { message ->
                        ChatMessageItem(
                            message = message,
                            onCopyCode = handleCopyCode,
                            onRunCode = { submitPrompt(it) },
                            onResendPrompt = { viewModel.updateInputText(it) },
                            terminalTheme = uiState.terminalTheme,
                            onToggleThinking = { messageId ->
                                viewModel.toggleThinkingExpanded(messageId)
                            },
                            onToggleAction = { messageId ->
                                viewModel.toggleActionExpanded(messageId)
                            },
                            onFileClick = { filePath ->
                                viewModel.openFileViewer(filePath)
                            },
                            onOpenInBrowser = { filePath ->
                                viewModel.openFileOnPc(filePath)
                            },
                            onRetryClick = {
                                viewModel.retryLastMessage()
                            },
                            onRetryStrongClick = { messageId ->
                                viewModel.retryOnStrong(messageId)
                            },
                            onDeleteClick = { messageId ->
                                viewModel.deleteChatMessage(messageId)
                            },
                            onReviewClick = {
                                viewModel.reviewSessionFiles()
                            },
                            onUndoWrite = { path, previous ->
                                viewModel.undoFileWrite(path, previous)
                            },
                            onDiagnoseClick = viewModel::askAgentToDiagnose,
                            onCopyClick = { handleCopyCode(it) },
                            onRegenerateClick = { viewModel.regenerateLastAssistant() },
                            onEditClick = { viewModel.updateInputText(it) },
                            onOpenTask = { taskId ->
                                viewModel.openSoftwareTasks()
                                viewModel.selectSoftwareTask(taskId)
                            }
                        )
                    }
                }
            }
        }
        }
    }
    }

    if (uiState.showPlanDialog) {
        PlanDialog(
            plan = uiState.planPreview,
            onApprove = { viewModel.approvePlanAndSend() },
            onDismiss = { viewModel.closePlanDialog() }
        )
    }

    if (uiState.showReviewDialog) {
        ReviewDialog(
            findings = uiState.reviewFindings,
            onDismiss = { viewModel.closeReviewDialog() }
        )
    }

    // Settings Dialog
    if (uiState.showSettingsDialog) {
        SettingsDialog(
            currentMode = uiState.connectionMode,
            currentAgentUrl = uiState.agentBaseUrl,
            currentPureChatUrl = uiState.pureChatBaseUrl,
            currentSystemPrompt = uiState.systemPrompt,
            currentModel = uiState.selectedModel,
            currentTerminalTheme = uiState.terminalTheme,
            availableModels = uiState.availableModels,
            forceLegacyChat = uiState.forceLegacyChat,
            onForceLegacyChatChange = { viewModel.setForceLegacyChat(it) },
            delegateLongWork = uiState.delegateLongWork,
            onDelegateLongWorkChange = { viewModel.setDelegateLongWork(it) },
            notifyReplies = uiState.notifyReplies,
            onNotifyRepliesChange = { viewModel.setNotificationPrefs(it, uiState.notifyDownloads, uiState.notifyTasks) },
            notifyDownloads = uiState.notifyDownloads,
            onNotifyDownloadsChange = { viewModel.setNotificationPrefs(uiState.notifyReplies, it, uiState.notifyTasks) },
            notifyTasks = uiState.notifyTasks,
            onNotifyTasksChange = { viewModel.setNotificationPrefs(uiState.notifyReplies, uiState.notifyDownloads, it) },
            allowedTools = uiState.allowedTools,
            onToolAllowedChange = { name, allowed -> viewModel.setToolAllowed(name, allowed) },
            language = uiState.language,
            onLanguageChange = { code ->
                viewModel.setLanguage(code)
                (context as? android.app.Activity)?.recreate()
            },
            onRefreshModels = { viewModel.refreshServerCapabilities() },
            onDismiss = { viewModel.closeSettings() },
            onSave = { mode, agentUrl, pureChatUrl, systemPrompt, model, terminalTheme ->
                viewModel.saveSettings(mode, agentUrl, pureChatUrl, systemPrompt, model, terminalTheme)
            }
        )
    }

    pendingCommand?.let { prompt ->
        AlertDialog(
            onDismissRequest = { pendingCommand = null },
            title = { Text("Approve workstation command?") },
            text = {
                Column {
                    Text("This command will run on the remote Ubuntu workstation:")
                    Text(
                        prompt.removePrefix("Run command:").trim(),
                        modifier = Modifier.padding(top = 10.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingCommand = null
                    val command = prompt.substringAfter("Run command:", "").trim()
                    withCompletionNotifications { viewModel.runDirectCommand(command) }
                }) { Text("Approve & Run") }
            },
            dismissButton = {
                TextButton(onClick = { pendingCommand = null }) { Text("Cancel") }
            }
        )
    }

    if (uiState.showBridgeToolsDialog) {
        BridgeToolsDialog(
            busy = uiState.bridgeToolsBusy || uiState.isStreaming || uiState.isStartingJob,
            result = uiState.bridgeToolsResult,
            onDirectCommand = { command ->
                focusManager.clearFocus()
                withCompletionNotifications { viewModel.runDirectCommand(command, timeoutSeconds = 600) }
            },
            onBackgroundCommand = { command ->
                focusManager.clearFocus()
                withCompletionNotifications { viewModel.startBackgroundCommand(command) }
            },
            onSearch = viewModel::searchWeb,
            onProcesses = viewModel::fetchProcesses,
            onService = viewModel::operateService,
            onPackage = { action, packageName ->
                val operation = { viewModel.operatePackage(action, packageName) }
                if (action in setOf("install", "remove", "update-index", "upgrade")) withCompletionNotifications(operation)
                else operation()
            },
            onSchedule = { action, schedule, value ->
                viewModel.operateSchedule(
                    if (action == "cancel") com.example.mylocalai.model.ScheduleRequest(action = action, unit = value)
                    else com.example.mylocalai.model.ScheduleRequest(action = action, schedule = schedule, command = value)
                )
            },
            onApps = viewModel::queryApps,
            onLaunchApp = viewModel::launchRemoteApp,
            onDesktop = viewModel::desktopAction,
            onDesktopDiagnostics = viewModel::fetchDesktopDiagnostics,
            onHealth = viewModel::openHealthDiagnostics,
            onLogs = viewModel::fetchBridgeLogs,
            onTags = viewModel::fetchModelTags,
            onOpenJobs = { withCompletionNotifications { viewModel.openJobs() } },
            onSoftwareTasks = viewModel::openSoftwareTasks,
            onDismiss = viewModel::closeBridgeTools
        )
    }

    if (uiState.showSoftwareTasksDialog) {
        SoftwareTasksDialog(
            tasks = uiState.softwareTasks,
            selectedTaskId = uiState.selectedSoftwareTaskId,
            events = uiState.softwareTaskEvents,
            models = uiState.availableModels,
            selectedModel = uiState.selectedModel,
            loading = uiState.softwareTaskLoading,
            restoring = uiState.softwareTasksRestoring,
            creating = uiState.softwareTaskCreating,
            error = uiState.softwareTaskError,
            pendingCreate = uiState.pendingSoftwareTaskCreate != null,
            diff = uiState.softwareTaskDiff,
            diffLoading = uiState.softwareTaskDiffLoading,
            deleteConfirmation = uiState.softwareTaskDeleteConfirmation,
            onSelectModel = viewModel::selectModel,
            onCreate = { request -> withCompletionNotifications { viewModel.createSoftwareTask(request) } },
            onRetryCreate = viewModel::retryPendingSoftwareTaskCreate,
            onSelectTask = viewModel::selectSoftwareTask,
            onRefreshTask = viewModel::refreshSoftwareTaskNow,
            onResume = viewModel::resumeSoftwareTask,
            onCancelTask = viewModel::cancelSoftwareTask,
            onLoadDiff = viewModel::loadSoftwareTaskDiff,
            onCloseDiff = viewModel::closeSoftwareTaskDiff,
            onExportDiff = { diff ->
                pendingTaskDiffExport = diff
                val timestamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())
                saveTaskDiffLauncher.launch("agent-task-diff-$timestamp.diff")
            },
            onConfirmDeleteWorktree = viewModel::confirmDeleteSoftwareTaskWorktree,
            onDismissDeleteWorktree = viewModel::dismissDeleteSoftwareTaskWorktree,
            onDeleteWorktree = viewModel::deleteSoftwareTaskWorktree,
            onDismiss = viewModel::closeSoftwareTasks,
        )
    }

    if (uiState.showJobsDialog) {
        JobsDialog(
            jobs = uiState.jobs,
            loading = uiState.jobsLoading,
            error = uiState.jobsError,
            onCancel = viewModel::cancelBackgroundJob,
            retryableCommand = uiState.retryableJobCommand,
            retrySecondsRemaining = uiState.jobRetrySecondsRemaining,
            retryDelaySeconds = uiState.jobRetryDelaySeconds,
            onRetryAfterDelay = viewModel::retryQueuedJobAfterDelay,
            onCancelRetry = viewModel::cancelQueuedJobRetry,
            onDismiss = viewModel::closeJobs
        )
    }

    if (uiState.showAuditHistoryDialog) {
        BridgeAuditHistoryDialog(
            entries = uiState.bridgeAuditEntries,
            onClear = viewModel::clearAuditHistory,
            onDismiss = viewModel::closeAuditHistory,
        )
    }

    if (uiState.showRemoteFilesDialog) {
        RemoteFileExplorerDialog(
            path = uiState.remoteFilesPath,
            entries = uiState.remoteFiles,
            isLoading = uiState.isLoadingRemoteFiles,
            error = uiState.remoteFilesError,
            truncated = uiState.remoteFilesTruncated,
            elevated = uiState.remoteFilesElevated,
            asRoot = uiState.remoteFilesAsRoot,
            onNavigateTo = { viewModel.refreshRemoteFiles(it) },
            onNavigateUp = { viewModel.navigateRemoteFilesUp() },
            onRefresh = { viewModel.refreshRemoteFiles() },
            onEntryClick = { viewModel.openRemoteFile(it) },
            onDownloadClick = { viewModel.downloadRemoteFileToPhone(it.path, asRoot = uiState.remoteFilesAsRoot) },
            onUploadClick = { remoteUploadLauncher.launch("*/*") },
            onCopyClick = { viewModel.transferRemoteFile(it.path, uiState.remoteFilesPath, "copy") },
            onMoveClick = { viewModel.transferRemoteFile(it.path, uiState.remoteFilesPath, "move") },
            onDeleteClick = { viewModel.deleteFile(it.path, asRoot = uiState.remoteFilesAsRoot) },
            onToggleRoot = { viewModel.setRemoteFilesAsRoot(it) },
            onDismiss = { viewModel.closeRemoteFiles() }
        )
    }

    if (uiState.showQueueDialog) {
        ChatQueueDialog(
            items = uiState.queuedPrompts,
            onMoveUp = { viewModel.moveQueuedPrompt(it, -1) },
            onMoveDown = { viewModel.moveQueuedPrompt(it, 1) },
            onCancel = { viewModel.cancelQueuedPrompt(it) },
            onDismiss = { viewModel.closeQueueDialog() }
        )
    }

    // System Stats Dialog
    if (uiState.showStatsDialog) {
        SystemStatsDialog(
            stats = uiState.systemStats,
            isLoading = uiState.isFetchingStats,
            serverMetrics = uiState.serverHealthMetrics,
            serverMetricsLoading = uiState.serverMetricsLoading,
            serverMetricsError = uiState.serverMetricsError,
            activeChatJobElapsedSeconds = uiState.activeChatJobElapsedSeconds,
            tasks = uiState.softwareTasks,
            onRefresh = { viewModel.fetchSystemStats(); viewModel.fetchServerMetrics() },
            onViewServerLogs = { viewModel.openServerLogs() },
            onDismiss = { viewModel.closeSystemStats() }
        )
    }

    // Server Logs Dialog
    if (uiState.showServerLogsDialog) {
        ServerLogsDialog(
            logs = uiState.serverLogs,
            isLoading = uiState.isFetchingLogs,
            error = uiState.serverLogsError,
            onRefresh = { viewModel.fetchServerLogs() },
            onCopyLogs = handleCopyCode,
            saveMessage = uiState.serverLogsSaveMessage,
            saveError = uiState.serverLogsSaveError,
            onSaveLogs = {
                val timestamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())
                saveServerLogsLauncher.launch("agent-bridge-logs-$timestamp.txt")
            },
            onDismiss = { viewModel.closeServerLogs() }
        )
    }

    // File Preview & Delete Dialog
    uiState.filePreviewState?.let { previewState ->
        FileViewerDialog(
            previewState = previewState,
            onDownloadClick = { path ->
                viewModel.downloadRemoteFileToPhone(path, asRoot = uiState.remoteFilesAsRoot)
            },
            onCopyContent = handleCopyCode,
            onDeleteFile = { filePath ->
                viewModel.deleteFile(filePath)
            },
            onRetryDownload = viewModel::retryFileDownload,
            onAskAboutImage = viewModel::askAboutScreenshot,
            onDismiss = { viewModel.closeFileViewer() }
        )
    }

    if (uiState.showAgentNotesDialog) {
        AgentNotesDialog(
            notes = uiState.agentNotes,
            loading = uiState.notesLoading,
            posting = uiState.notesPosting,
            error = uiState.notesError,
            draft = uiState.notesDraft,
            onDraftChange = viewModel::updateNotesDraft,
            onSync = viewModel::syncAgentNotes,
            onPost = viewModel::postAgentNote,
            onDismiss = viewModel::closeAgentNotes
        )
    }

    if (uiState.showHealthDialog) {
        HealthDiagnosticsDialog(
            loading = uiState.healthLoading,
            result = uiState.healthResult,
            error = uiState.healthError,
            stats = uiState.systemStats,
            selectedModel = uiState.selectedModel,
            agentBaseUrl = uiState.agentBaseUrl,
            availableModels = uiState.availableModels,
            onRefresh = {
                viewModel.fetchHealth()
                viewModel.fetchSystemStats()
            },
            onDismiss = viewModel::closeHealthDiagnostics
        )
    }

    // Clear conversation confirmation dialog
    if (uiState.showClearConversationDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissClearConversationDialog,
            title = { Text("Clear conversation") },
            text = { Text("Clear server-side history for this conversation and remove local messages? The session will be kept for future use.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissClearConversationDialog()
                    viewModel.clearCurrentConversation()
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissClearConversationDialog) { Text("Cancel") }
            }
        )
    }

    // Clear all chats confirmation dialog
    if (uiState.showClearAllChatsDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissClearAllChatsDialog,
            title = { Text("Clear all chats") },
            text = { Text("Clear server-side history and local messages for all conversations? Session IDs are retained. Tasks, jobs, and notes are not affected.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissClearAllChatsDialog()
                    viewModel.clearAllChats()
                }) { Text("Clear all") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissClearAllChatsDialog) { Text("Cancel") }
            }
        )
    }

    // Clear chat error / retry dialog
    val clearError = uiState.clearChatError
    if (clearError != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissClearChatError,
            title = { Text("Clear failed") },
            text = { Text(clearError) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissClearChatError()
                    if (uiState.failedClearSessionIds.isNotEmpty()) {
                        viewModel.retryClearFailedChats()
                    } else {
                        viewModel.retryClearCurrentConversation()
                    }
                }) { Text("Retry") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissClearChatError) { Text("Dismiss") }
            }
        )
    }

    if (showUbuntuMenu) {
        AlertDialog(
            onDismissRequest = { showUbuntuMenu = false },
            title = { Text("Ubuntu PC") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    TextButton(onClick = {
                        showUbuntuMenu = false
                        viewModel.openRemoteFiles()
                    }, modifier = Modifier.fillMaxWidth()) { Text("Browse files (whole PC)") }
                    TextButton(onClick = {
                        showUbuntuMenu = false
                        viewModel.openBridgeTools()
                    }, modifier = Modifier.fillMaxWidth()) { Text("Bridge tools") }
                    TextButton(onClick = {
                        showUbuntuMenu = false
                        withCompletionNotifications { viewModel.openJobs() }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Background jobs") }
                    TextButton(onClick = {
                        showUbuntuMenu = false
                        viewModel.openAuditHistory()
                    }, modifier = Modifier.fillMaxWidth()) { Text("Action history") }
                    TextButton(onClick = {
                        showUbuntuMenu = false
                        viewModel.openAgentNotes()
                    }, modifier = Modifier.fillMaxWidth()) { Text("Sync notes") }
                    TextButton(onClick = {
                        showUbuntuMenu = false
                        viewModel.openSystemStats()
                    }, modifier = Modifier.fillMaxWidth()) { Text("System stats & health") }
                    TextButton(onClick = {
                        showUbuntuMenu = false
                        viewModel.openServerLogs()
                    }, modifier = Modifier.fillMaxWidth()) { Text("Server logs") }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    Text("Quick workstation commands", style = MaterialTheme.typography.labelMedium)
                    defaultMacroShortcuts.forEach { macro ->
                        TextButton(onClick = {
                            showUbuntuMenu = false
                            submitPrompt(macro.prompt)
                        }, modifier = Modifier.fillMaxWidth()) { Text("${macro.icon} ${macro.label}") }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    TextButton(onClick = {
                        showUbuntuMenu = false
                        viewModel.requestClearCurrentConversation()
                    }, modifier = Modifier.fillMaxWidth()) { Text("Clear this conversation") }
                    TextButton(onClick = {
                        showUbuntuMenu = false
                        viewModel.requestClearAllChats()
                    }, modifier = Modifier.fillMaxWidth()) { Text("Clear all chats") }
                }
            },
            confirmButton = { TextButton(onClick = { showUbuntuMenu = false }) { Text("Close") } }
        )
    }
}

@Composable
private fun ChatEmptyState(
    connectionMode: ConnectionMode,
    isConnected: Boolean,
    onSuggestionClick: (String) -> Unit,
    onOpenFilesClick: () -> Unit,
    onOpenStatsClick: () -> Unit,
    onContinueTask: () -> Unit = {},
    canContinueTask: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            shape = CircleShape,
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "MY local AI",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(6.dp))

        Surface(
            color = if (isConnected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
            shape = CircleShape
        ) {
            Text(
                text = if (isConnected) {
                    if (connectionMode == ConnectionMode.AGENT) "⚡ Ubuntu Agent Bridge Active" else "💬 Pure Chat Mode Active"
                } else {
                    "🔴 Offline · Tap top indicator to retry"
                },
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Quick Actions",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(10.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EmptyStateActionCard(
                icon = "📁",
                title = "Browse PC files",
                subtitle = "Read, write, upload and download across the Ubuntu host",
                onClick = onOpenFilesClick
            )
            EmptyStateActionCard(
                icon = "📊",
                title = "Health & diagnostics",
                subtitle = "Memory, CPU, workers, logs and network status",
                onClick = onOpenStatsClick
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Try a prompt",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AssistChip(
                onClick = { onSuggestionClick("What can you tell me about this Ubuntu PC?") },
                label = { Text("🖥️ " + stringResource(R.string.empty_browse)) }
            )
            AssistChip(
                onClick = { onSuggestionClick("Create a small website with HTML and CSS in a folder on the Desktop") },
                label = { Text("🌐 " + stringResource(R.string.empty_build_site)) }
            )
            AssistChip(
                onClick = { onSuggestionClick("Show the last 30 lines of the agent-bridge journal") },
                label = { Text("📜 " + stringResource(R.string.empty_logs)) }
            )
            AssistChip(
                onClick = { onSuggestionClick("Explain a file in ~/agent-bridge") },
                label = { Text("📄 " + stringResource(R.string.empty_explain_file)) }
            )
            if (canContinueTask) {
                AssistChip(
                    onClick = onContinueTask,
                    label = { Text("↻ " + stringResource(R.string.empty_continue)) }
                )
            }
        }
    }
}

@Composable
private fun EmptyStateActionCard(
    icon: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, fontSize = 20.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

