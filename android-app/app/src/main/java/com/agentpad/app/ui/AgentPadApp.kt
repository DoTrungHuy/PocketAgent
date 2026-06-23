package com.agentpad.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PendingActions
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agentpad.app.BuildConfig
import com.agentpad.app.data.ThemePreference
import com.agentpad.app.domain.AgentErrorKind
import com.agentpad.app.domain.AgentThread
import com.agentpad.app.domain.ApprovalScope
import com.agentpad.app.domain.MessageKind
import com.agentpad.app.domain.MessageRole
import com.agentpad.app.domain.PlannedAction
import com.agentpad.app.domain.ProviderPresets
import com.agentpad.app.domain.RiskLevel
import com.agentpad.app.domain.TaskPlan
import com.agentpad.app.domain.ThreadMessage
import com.agentpad.app.domain.TurnStatus
import com.agentpad.app.ui.theme.AgentPadTheme
import com.agentpad.app.ui.theme.Danger
import com.agentpad.app.ui.theme.Success
import com.agentpad.app.ui.theme.Warning
import java.text.DateFormat
import java.util.Date

private val primarySections = listOf(
    AppSection.THREAD to "Chat",
    AppSection.PLAN to "Tasks",
    AppSection.SETTINGS to "Settings"
)

@Composable
fun AgentPadRoot(
    viewModel: AgentPadViewModel,
    onChooseDocument: () -> Unit,
    onPrivacyModeChanged: (Boolean) -> Unit,
    onExportDiagnostics: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val threads by viewModel.threads.collectAsStateWithLifecycle()

    LaunchedEffect(state.privacyMode) {
        onPrivacyModeChanged(state.privacyMode)
    }

    AgentPadTheme(darkTheme = state.theme == ThemePreference.DARK) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            LaunchedEffect(state.section, maxWidth) {
                viewModel.recordUiContext(state.section, maxWidth.value.toInt())
            }
            when {
                maxWidth < 680.dp -> CompactLayout(state, threads, viewModel, onExportDiagnostics)
                else -> DesktopLayout(state, threads, viewModel, onExportDiagnostics)
            }
        }
    }

    Dialogs(state, viewModel, onExportDiagnostics)
}

@Composable
private fun Dialogs(
    state: AgentPadUiState,
    viewModel: AgentPadViewModel,
    onExportDiagnostics: () -> Unit
) {
    if (state.compressionRequired) {
        AlertDialog(
            onDismissRequest = viewModel::dismissCompression,
            title = { Text("上下文较长") },
            text = { Text("继续前需要生成一个检查点。原始消息会保留，后续请求会使用摘要。") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmCompressionAndCreatePlan) {
                    Text("生成检查点")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissCompression) { Text("取消") }
            }
        )
    }

    if (state.deleteConfirmationThreadId != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteThread,
            title = { Text("删除会话？") },
            text = { Text("会删除这个会话里的消息、任务和结果。这个操作不能撤销。") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDeleteThread) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteThread) { Text("取消") }
            }
        )
    }

    if (
        state.crashReportAvailable &&
        !state.compressionRequired &&
        state.deleteConfirmationThreadId == null
    ) {
        AlertDialog(
            onDismissRequest = viewModel::dismissCrashReport,
            title = { Text("检测到上次异常") },
            text = { Text("AgentPad 保存了一份本地诊断报告，不包含 API Key、文件原文或完整模型输出。") },
            confirmButton = {
                TextButton(onClick = onExportDiagnostics) { Text("导出诊断") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissCrashReport) { Text("忽略") }
            }
        )
    }
}

@Composable
private fun CompactLayout(
    state: AgentPadUiState,
    threads: List<AgentThread>,
    viewModel: AgentPadViewModel,
    onExportDiagnostics: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { WorkspaceHeader(state, compact = true) },
        bottomBar = {
            NavigationBar(windowInsets = WindowInsets.navigationBars) {
                primarySections.forEach { (section, label) ->
                    NavigationBarItem(
                        selected = selectedPrimarySection(state.section) == section,
                        onClick = { viewModel.setSection(section) },
                        icon = { Icon(sectionIcon(section), label) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            SectionContent(
                state = state,
                threads = threads,
                viewModel = viewModel,
                onExportDiagnostics = onExportDiagnostics,
                showCompactThreads = true
            )
        }
    }
}

@Composable
private fun DesktopLayout(
    state: AgentPadUiState,
    threads: List<AgentThread>,
    viewModel: AgentPadViewModel,
    onExportDiagnostics: () -> Unit
) {
    Row(Modifier.fillMaxSize()) {
        ThreadSidebar(
            threads = threads,
            selectedId = state.selectedThreadId,
            section = selectedPrimarySection(state.section),
            viewModel = viewModel,
            modifier = Modifier.width(286.dp)
        )
        VerticalDivider(color = MaterialTheme.colorScheme.outline)
        Column(Modifier.weight(1f).fillMaxHeight()) {
            WorkspaceHeader(state, compact = false)
            SectionContent(
                state = state,
                threads = threads,
                viewModel = viewModel,
                onExportDiagnostics = onExportDiagnostics,
                showCompactThreads = false
            )
        }
    }
}

@Composable
private fun WorkspaceHeader(state: AgentPadUiState, compact: Boolean) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = if (compact) 10.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "AgentPad",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    state.snapshot?.thread?.title ?: "New task",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            ModelBadge(state)
        }
    }
}

@Composable
private fun ModelBadge(state: AgentPadUiState) {
    val provider = ProviderPresets.byId(state.providerSettings.providerId)?.name
        ?: state.providerSettings.providerId.ifBlank { "未配置" }
    AssistChip(
        onClick = {},
        enabled = false,
        label = {
            Text(
                if (state.apiKeyConfigured) "$provider · ${state.providerSettings.model}" else "设置模型",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}

@Composable
private fun ThreadSidebar(
    threads: List<AgentThread>,
    selectedId: String?,
    section: AppSection,
    viewModel: AgentPadViewModel,
    modifier: Modifier
) {
    Surface(modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("AP", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("AgentPad", fontWeight = FontWeight.Bold)
                    Text("local workspace", style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(18.dp))
            Button(onClick = viewModel::newThread, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("New chat")
            }
            Spacer(Modifier.height(18.dp))
            primarySections.forEach { (target, label) ->
                SidebarDestination(
                    label = label,
                    section = target,
                    selected = section == target,
                    viewModel = viewModel
                )
            }
            Spacer(Modifier.height(18.dp))
            Text("Recent", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(threads, key = { it.id }) { thread ->
                    ThreadRow(
                        thread = thread,
                        selected = thread.id == selectedId,
                        onOpen = { viewModel.openThread(thread.id) },
                        onDelete = { viewModel.requestDeleteThread(thread.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SidebarDestination(
    label: String,
    section: AppSection,
    selected: Boolean,
    viewModel: AgentPadViewModel
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { viewModel.setSection(section) }
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(sectionIcon(section), null, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

@Composable
private fun ThreadRow(
    thread: AgentThread,
    selected: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(thread.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(Date(thread.updatedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Delete, "删除会话", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SectionContent(
    state: AgentPadUiState,
    threads: List<AgentThread>,
    viewModel: AgentPadViewModel,
    onExportDiagnostics: () -> Unit,
    showCompactThreads: Boolean
) {
    when (selectedPrimarySection(state.section)) {
        AppSection.THREAD -> ConversationPage(state, threads, viewModel, showCompactThreads)
        AppSection.PLAN -> TasksPage(state, viewModel)
        AppSection.SETTINGS -> SettingsPage(state, viewModel, onExportDiagnostics)
        else -> ConversationPage(state, threads, viewModel, showCompactThreads)
    }
}

@Composable
private fun ConversationPage(
    state: AgentPadUiState,
    threads: List<AgentThread>,
    viewModel: AgentPadViewModel,
    showCompactThreads: Boolean
) {
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showCompactThreads && threads.isNotEmpty()) {
                item { CompactThreads(threads, state.selectedThreadId, viewModel) }
            }
            val messages = state.snapshot?.messages.orEmpty()
            if (messages.isEmpty()) {
                item { EmptyConversation() }
            } else {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(message)
                }
            }
            state.currentPlan?.let { plan ->
                item { InlineTaskCard(state, plan, viewModel) }
            }
            state.resultNotice?.let { notice ->
                item { NoticeCard(notice, Success) }
            }
            state.error?.let { error ->
                item { NoticeCard("${errorPrefix(state.lastErrorKind)}$error", MaterialTheme.colorScheme.error) }
            }
        }
        PromptBar(state, viewModel)
    }
}

@Composable
private fun CompactThreads(
    threads: List<AgentThread>,
    selectedId: String?,
    viewModel: AgentPadViewModel
) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Recent chats", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = viewModel::newThread) { Text("New") }
        }
        threads.take(3).forEach { thread ->
            ThreadRow(
                thread = thread,
                selected = thread.id == selectedId,
                onOpen = { viewModel.openThread(thread.id) },
                onDelete = { viewModel.requestDeleteThread(thread.id) }
            )
        }
    }
}

@Composable
private fun EmptyConversation() {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(10.dp).size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("New chat", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ThreadMessage) {
    val isUser = message.role == MessageRole.USER
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            contentColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(8.dp),
            border = if (isUser) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth(if (isUser) 0.86f else 0.92f)
        ) {
            Column(Modifier.padding(13.dp)) {
                Text(messageLabel(message), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text(message.content)
                Text(
                    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun InlineTaskCard(
    state: AgentPadUiState,
    plan: TaskPlan,
    viewModel: AgentPadViewModel
) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Description, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(plan.title, fontWeight = FontWeight.Bold)
                Text(statusLabel(state.currentTurn?.status), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RiskBadge(plan.highestRisk)
        }
        Spacer(Modifier.height(10.dp))
        Text(plan.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ApprovalButton(state, plan, viewModel)
            Button(onClick = viewModel::executePlan, enabled = !state.busy) {
                Icon(Icons.Rounded.PlayArrow, null)
                Spacer(Modifier.width(6.dp))
                Text("Run")
            }
            OutlinedButton(onClick = viewModel::cancelTurn, enabled = state.currentTurn != null && !isFinished(state.currentTurn?.status)) {
                Icon(Icons.Rounded.StopCircle, null)
                Spacer(Modifier.width(6.dp))
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun ApprovalButton(
    state: AgentPadUiState,
    plan: TaskPlan,
    viewModel: AgentPadViewModel
) {
    val needsTaskApproval = viewModel.approvalsFor(plan).any { it.second == ApprovalScope.TASK }
    val approved = !needsTaskApproval || viewModel.isTaskApproved(state, plan)
    if (approved) {
        FilledTonalButton(onClick = {}, enabled = false) {
            Icon(Icons.Rounded.CheckCircle, null)
            Spacer(Modifier.width(6.dp))
            Text("Approved")
        }
    } else {
        FilledTonalButton(onClick = viewModel::approveTask, enabled = !state.busy) {
            Icon(Icons.Rounded.Security, null)
            Spacer(Modifier.width(6.dp))
            Text("Approve")
        }
    }
}

@Composable
private fun PromptBar(state: AgentPadUiState, viewModel: AgentPadViewModel) {
    Surface(tonalElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            OutlinedTextField(
                value = state.draftGoal,
                onValueChange = viewModel::setDraftGoal,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Ask AgentPad to do something...") },
                minLines = 1,
                maxLines = 4,
                enabled = !state.busy
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (state.busy) "Working..." else "",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f)
                )
                if (state.busy) {
                    OutlinedButton(onClick = viewModel::cancelTurn) { Text("Cancel") }
                }
                Button(
                    onClick = viewModel::createPlan,
                    enabled = state.draftGoal.isNotBlank() && !state.busy
                ) {
                    Text(if (state.currentPlan == null) "Plan" else "New plan")
                }
            }
        }
    }
}

@Composable
private fun TasksPage(state: AgentPadUiState, viewModel: AgentPadViewModel) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { PageHeading("Tasks") }
        val plan = state.currentPlan
        if (plan == null) {
            item {
                Panel {
                    Text("No active task", fontWeight = FontWeight.Bold)
                }
            }
            return@LazyColumn
        }
        item { TaskSummaryCard(state, plan, viewModel) }
        items(plan.actions, key = { it.id }) { action ->
            ActionRow(state, plan, action, viewModel)
        }
    }
}

@Composable
private fun TaskSummaryCard(
    state: AgentPadUiState,
    plan: TaskPlan,
    viewModel: AgentPadViewModel
) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(plan.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(statusLabel(state.currentTurn?.status), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RiskBadge(plan.highestRisk)
        }
        Spacer(Modifier.height(10.dp))
        Text(plan.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ApprovalButton(state, plan, viewModel)
            Button(onClick = viewModel::executePlan, enabled = !state.busy) { Text("Run task") }
            OutlinedButton(onClick = viewModel::cancelTurn, enabled = !state.busy && !isFinished(state.currentTurn?.status)) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun ActionRow(
    state: AgentPadUiState,
    plan: TaskPlan,
    action: PlannedAction,
    viewModel: AgentPadViewModel
) {
    val scope = viewModel.approvalsFor(plan).firstOrNull { it.first == action.id }?.second ?: ApprovalScope.NONE
    val approved = when (scope) {
        ApprovalScope.NONE -> true
        ApprovalScope.TASK -> viewModel.isTaskApproved(state, plan)
        ApprovalScope.ACTION -> viewModel.isActionApproved(state, plan, action.id)
    }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = if (approved) Success.copy(alpha = 0.14f) else Warning.copy(alpha = 0.16f)
            ) {
                Icon(
                    if (approved) Icons.Rounded.CheckCircle else Icons.Rounded.PendingActions,
                    null,
                    tint = if (approved) Success else Warning,
                    modifier = Modifier.padding(8.dp).size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(action.title, fontWeight = FontWeight.Bold)
                Text(action.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${riskLabel(action.risk)} · ${action.tool}", style = MaterialTheme.typography.labelSmall)
            }
            if (scope == ApprovalScope.ACTION && !approved) {
                FilledTonalButton(onClick = { viewModel.approveAction(action.id) }, enabled = !state.busy) {
                    Text("Allow")
                }
            }
        }
    }
}

@Composable
private fun SettingsPage(
    state: AgentPadUiState,
    viewModel: AgentPadViewModel,
    onExportDiagnostics: () -> Unit
) {
    val settings = state.providerSettings
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { PageHeading("Settings") }
        item {
            Panel {
                Text("Provider", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                ProviderPresets.all.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { preset ->
                            val selected = settings.providerId == preset.id
                            if (selected) {
                                Button(
                                    onClick = { viewModel.selectProvider(preset.id) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(preset.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { viewModel.selectProvider(preset.id) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(preset.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        item {
            Panel {
                Text("Connection", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = settings.endpoint,
                    onValueChange = { viewModel.setProviderSettings(settings.copy(endpoint = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Endpoint") },
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = settings.model,
                    onValueChange = { viewModel.setProviderSettings(settings.copy(model = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Model") },
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = state.apiKeyDraft,
                    onValueChange = viewModel::setApiKeyDraft,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (state.apiKeyConfigured) "API Key (leave blank to keep)" else "API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = viewModel::testAndSaveProvider,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Test and save")
                }
            }
        }
        item {
            Panel {
                Text("App", fontWeight = FontWeight.Bold)
                SettingSwitchRow(
                    title = "Dark theme",
                    checked = state.theme == ThemePreference.DARK,
                    onChange = {
                        viewModel.setTheme(if (it) ThemePreference.DARK else ThemePreference.LIGHT)
                    }
                )
                SettingSwitchRow(
                    title = "Privacy mode",
                    checked = state.privacyMode,
                    description = "Blocks screenshots and screen recording.",
                    onChange = viewModel::setPrivacyMode
                )
            }
        }
        item {
            Panel {
                Text("Version", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                Text("${BuildConfig.GIT_SHA} · ${BuildConfig.BUILD_CHANNEL}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onExportDiagnostics, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Download, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Export diagnostics")
                }
            }
        }
        state.resultNotice?.let { notice ->
            item { NoticeCard(notice, Success) }
        }
        state.error?.let { error ->
            item { NoticeCard("${errorPrefix(state.lastErrorKind)}$error", MaterialTheme.colorScheme.error) }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    description: String? = null,
    onChange: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Panel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

@Composable
private fun PageHeading(title: String, subtitle: String = "") {
    Column {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (subtitle.isNotBlank()) {
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NoticeCard(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Text(text, color = color, modifier = Modifier.padding(12.dp))
    }
}

@Composable
private fun RiskBadge(risk: RiskLevel) {
    val color = riskColor(risk)
    Text(
        riskLabel(risk),
        color = color,
        style = MaterialTheme.typography.labelMedium
    )
}

private fun selectedPrimarySection(section: AppSection): AppSection = when (section) {
    AppSection.THREAD -> AppSection.THREAD
    AppSection.PLAN, AppSection.APPROVALS, AppSection.CAPABILITIES -> AppSection.PLAN
    AppSection.SETTINGS -> AppSection.SETTINGS
}

private fun sectionIcon(section: AppSection) = when (section) {
    AppSection.THREAD -> Icons.Rounded.AutoAwesome
    AppSection.PLAN, AppSection.APPROVALS, AppSection.CAPABILITIES -> Icons.Rounded.Description
    AppSection.SETTINGS -> Icons.Rounded.Settings
}

private fun messageLabel(message: ThreadMessage): String = when {
    message.role == MessageRole.USER -> "You"
    message.kind == MessageKind.PLAN -> "Plan"
    message.kind == MessageKind.RESULT -> "Result"
    message.kind == MessageKind.CONTEXT_SUMMARY -> "Checkpoint"
    else -> "AgentPad"
}

private fun statusLabel(status: TurnStatus?): String = when (status) {
    TurnStatus.DRAFT -> "Draft"
    TurnStatus.PLANNING -> "Planning"
    TurnStatus.AWAITING_APPROVAL -> "Waiting for approval"
    TurnStatus.RUNNING -> "Running"
    TurnStatus.VERIFYING -> "Verifying"
    TurnStatus.COMPLETED -> "Completed"
    TurnStatus.FAILED -> "Failed"
    TurnStatus.CANCELLED -> "Cancelled"
    TurnStatus.INTERRUPTED -> "Interrupted"
    TurnStatus.SUPERSEDED -> "Superseded"
    null -> "No active task"
}

private fun isFinished(status: TurnStatus?): Boolean = status in setOf(
    TurnStatus.COMPLETED,
    TurnStatus.FAILED,
    TurnStatus.CANCELLED,
    TurnStatus.INTERRUPTED,
    TurnStatus.SUPERSEDED
)

private fun riskLabel(risk: RiskLevel): String = when (risk) {
    RiskLevel.READ_ONLY -> "read only"
    RiskLevel.TASK_APPROVAL -> "approval"
    RiskLevel.ACTION_APPROVAL -> "per action"
    RiskLevel.FORBIDDEN -> "blocked"
}

@Composable
private fun riskColor(risk: RiskLevel): Color = when (risk) {
    RiskLevel.READ_ONLY -> Success
    RiskLevel.TASK_APPROVAL -> MaterialTheme.colorScheme.primary
    RiskLevel.ACTION_APPROVAL -> Warning
    RiskLevel.FORBIDDEN -> Danger
}

private fun errorPrefix(kind: AgentErrorKind): String = when (kind) {
    AgentErrorKind.NONE -> ""
    AgentErrorKind.RATE_LIMITED -> "Rate limited: "
    AgentErrorKind.NETWORK_TIMEOUT -> "Timeout: "
    AgentErrorKind.PROVIDER_RETRYABLE -> "Provider unavailable: "
    AgentErrorKind.PROVIDER_REJECTED -> "Provider rejected: "
    AgentErrorKind.INVALID_RESPONSE -> "Invalid response: "
    AgentErrorKind.CANCELLED_BY_USER -> "Cancelled: "
    AgentErrorKind.LOCAL_FAILURE -> "Local error: "
}
