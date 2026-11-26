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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import com.example.mylocalai.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mylocalai.model.ConnectionMode
import com.example.mylocalai.ui.theme.ErrorRed
import com.example.mylocalai.ui.theme.SuccessGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBarComponent(
    isConnected: Boolean,
    connectionMode: ConnectionMode,
    availableModels: List<String>,
    selectedModel: String,
    isFetchingModels: Boolean = false,
    queuedCount: Int = 0,
    onQueueClick: () -> Unit = {},
    currentTaskGoal: String? = null,
    onModeToggle: (ConnectionMode) -> Unit,
    onModelSelected: (String) -> Unit,
    onNewChatClick: () -> Unit,
    onStatsClick: () -> Unit,
    onLogsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onClearConversationClick: () -> Unit = {},
    onClearAllChatsClick: () -> Unit = {},
    onRetryConnection: () -> Unit,
    onRefreshModels: () -> Unit = {},
    onExportChat: () -> Unit = {},
    onNavigationClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var dropdownExpanded by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = onNavigationClick) {
                    Icon(Icons.Default.Menu, contentDescription = "Open chat sessions")
                }
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(enabled = !isConnected) { onRetryConnection() }
                ) {
                    // Live Connection Indicator Dot
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isConnected) SuccessGreen else ErrorRed)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!currentTaskGoal.isNullOrBlank()) {
                            Text(
                                text = stringResource(R.string.continuing, currentTaskGoal.orEmpty()),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (!isConnected) {
                            Text(
                                text = stringResource(R.string.nav_offline),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1,
                            )
                        }
                    }
                }
            },
            actions = {
                if (queuedCount > 0) {
                    Text(
                        text = "⏳ $queuedCount",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .clickable { onQueueClick() }
                            .padding(8.dp)
                    )
                }
                IconButton(onClick = onNewChatClick) {
                    Icon(
                        imageVector = Icons.Default.AddComment,
                        contentDescription = "New Chat",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More actions",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Export chat") },
                            onClick = {
                                menuExpanded = false
                                onExportChat()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (isFetchingModels) "Refreshing models…" else "Refresh models") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            enabled = !isFetchingModels,
                            onClick = {
                                menuExpanded = false
                                onRefreshModels()
                            }
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        // Persistent Control Strip for Mode Toggle & Model Selector
        // Always 100% visible and accessible in both portrait and landscape!
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 12.dp, vertical = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mode Toggle Chip (Agent vs Pure Chat)
                Surface(
                    shape = CircleShape,
                    color = if (connectionMode == ConnectionMode.AGENT) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    shadowElevation = 1.dp,
                    modifier = Modifier.clickable {
                        val nextMode = if (connectionMode == ConnectionMode.AGENT) {
                            ConnectionMode.PURE_CHAT
                        } else {
                            ConnectionMode.AGENT
                        }
                        onModeToggle(nextMode)
                    }
                ) {
                    Text(
                        text = if (connectionMode == ConnectionMode.AGENT) "⚡ Agent Mode" else "💬 Pure Chat",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (connectionMode == ConnectionMode.AGENT) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                // Model Selector Dropdown Chip
                Box {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 1.dp,
                        modifier = Modifier.clickable { dropdownExpanded = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = selectedModel.ifBlank { "Select model" },
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 200.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            if (isFetchingModels) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Select Model",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        for (model in availableModels) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = model,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (model == selectedModel) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    onModelSelected(model)
                                    dropdownExpanded = false
                                }
                            )
                        }
                        if (availableModels.isNotEmpty()) {
                            HorizontalDivider()
                        }
                        DropdownMenuItem(
                            text = { Text(if (isFetchingModels) "Refreshing models…" else "Refresh models from server") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null
                                )
                            },
                            enabled = !isFetchingModels,
                            onClick = {
                                onRefreshModels()
                                dropdownExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
    }
}
