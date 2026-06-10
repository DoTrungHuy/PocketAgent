package com.agentpad.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PendingActions
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agentpad.app.domain.ApprovalScope
import com.agentpad.app.domain.CapabilityDescriptor
import com.agentpad.app.domain.CapabilityState
import com.agentpad.app.domain.PlannedAction
import com.agentpad.app.domain.RiskLevel
import com.agentpad.app.domain.TaskRecord
import com.agentpad.app.domain.TaskStatus
import com.agentpad.app.ui.theme.Danger
import com.agentpad.app.ui.theme.ElectricCyan
import com.agentpad.app.ui.theme.Graphite
import com.agentpad.app.ui.theme.GraphiteRaised
import com.agentpad.app.ui.theme.GraphiteSoft
import com.agentpad.app.ui.theme.Mist
import com.agentpad.app.ui.theme.Steel
import com.agentpad.app.ui.theme.Success
import com.agentpad.app.ui.theme.Warning
import java.text.DateFormat
import java.util.Date

private data class NavigationDestination(val section: AppSection, val label: String, val icon: ImageVector)

private val destinations = listOf(
    NavigationDestination(AppSection.TASKS, "任务", Icons.Rounded.AutoAwesome),
    NavigationDestination(AppSection.APPROVALS, "审批", Icons.Rounded.PendingActions),
    NavigationDestination(AppSection.CAPABILITIES, "能力", Icons.Rounded.Memory),
    NavigationDestination(AppSection.SETTINGS, "设置", Icons.Rounded.Settings)
)

@Composable
fun AgentPadRoot(viewModel: AgentPadViewModel, onChooseDocument: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val recentTasks by viewModel.recentTasks.collectAsStateWithLifecycle()

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(Graphite).windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val tablet = maxWidth >= 840.dp
        if (tablet) {
            Row(Modifier.fillMaxSize()) {
                AgentPadNavigationRail(state.section, viewModel::setSection)
                Workspace(state, recentTasks, viewModel.capabilities, true, viewModel, onChooseDocument, Modifier.weight(1f))
            }
        } else {
            Scaffold(
                containerColor = Graphite,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = { AgentPadBottomBar(state.section, viewModel::setSection) }
            ) { padding ->
                Workspace(
                    state,
                    recentTasks,
                    viewModel.capabilities,
                    false,
                    viewModel,
                    onChooseDocument,
                    Modifier.fillMaxSize().padding(padding)
                )
            }
        }
    }
}

@Composable
private fun Workspace(
    state: AgentPadUiState,
    recentTasks: List<TaskRecord>,
    capabilities: List<CapabilityDescriptor>,
    tablet: Boolean,
    viewModel: AgentPadViewModel,
    onChooseDocument: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.background(
            Brush.radialGradient(
                colors = listOf(Color(0x142DEAD6), Color.Transparent),
                center = Offset(100f, 40f),
                radius = 900f
            )
        )
    ) {
        TopStatusBar(state, onSetup = { viewModel.setSection(AppSection.SETTINGS) })
        AnimatedContent(targetState = state.section, label = "workspace-section", modifier = Modifier.weight(1f)) { section ->
            when (section) {
                AppSection.TASKS -> {
                    if (state.apiKeyConfigured) {
                        TasksScreen(state, recentTasks, tablet, viewModel, onChooseDocument)
                    } else {
                        WelcomeSetupScreen { viewModel.setSection(AppSection.SETTINGS) }
                    }
                }
                AppSection.APPROVALS -> ApprovalsScreen(state, viewModel)
                AppSection.CAPABILITIES -> CapabilitiesScreen(capabilities, state.apiKeyConfigured)
                AppSection.SETTINGS -> SettingsScreen(state, viewModel)
            }
        }
    }
}

@Composable
private fun TopStatusBar(state: AgentPadUiState, onSetup: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(ElectricCyan), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = Graphite)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("AgentPad", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Android AI Agent Workspace · ${statusLabel(state.status)}", style = MaterialTheme.typography.labelMedium, color = Steel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (state.apiKeyConfigured) StatusPill("模型已连接", Success) else OutlinedButton(onClick = onSetup) { Text("配置模型") }
    }
}

@Composable
private fun AgentPadNavigationRail(current: AppSection, onSelect: (AppSection) -> Unit) {
    NavigationRail(modifier = Modifier.fillMaxHeight().width(92.dp), containerColor = Color(0xFF05070A)) {
        Spacer(Modifier.height(16.dp))
        destinations.forEach { destination ->
            NavigationRailItem(selected = current == destination.section, onClick = { onSelect(destination.section) }, icon = { Icon(destination.icon, destination.label) }, label = { Text(destination.label) })
        }
    }
}

@Composable
private fun AgentPadBottomBar(current: AppSection, onSelect: (AppSection) -> Unit) {
    NavigationBar(containerColor = Color(0xFF05070A), windowInsets = WindowInsets.navigationBars) {
        destinations.forEach { destination ->
            NavigationBarItem(selected = current == destination.section, onClick = { onSelect(destination.section) }, icon = { Icon(destination.icon, destination.label) }, label = { Text(destination.label) })
        }
    }
}

@Composable
private fun WelcomeSetupScreen(onStart: () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            AgentCard(Modifier.fillMaxWidth(), colors = listOf(Color(0xFF101A20), Color(0xFF090D11))) {
                Text("AgentPad", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("让 Android 设备成为安全可控的 AI Agent 工作台。", color = Steel)
                Spacer(Modifier.height(18.dp))
                SetupStep("01", "选择模型服务商", "先使用 DeepSeek 快捷配置，后续逐步加入更多预设。")
                SetupStep("02", "输入自己的 API Key", "密钥保存在 Android Keystore，不进入日志。")
                SetupStep("03", "先规划、再审批、后执行", "Agent 不能绕过本地审批执行敏感操作。")
                Spacer(Modifier.height(20.dp))
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("开始配置模型") }
            }
        }
    }
}

@Composable
private fun SetupStep(number: String, title: String, body: String) {
    Row(modifier = Modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
        StatusPill(number, ElectricCyan)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(body, color = Steel, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TasksScreen(state: AgentPadUiState, recentTasks: List<TaskRecord>, tablet: Boolean, viewModel: AgentPadViewModel, onChooseDocument: () -> Unit) {
    if (tablet) {
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            LazyColumn(modifier = Modifier.weight(0.92f), contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { CommandCenter(state, viewModel, onChooseDocument) }
                item { RecentTasks(recentTasks) }
            }
            LazyColumn(modifier = Modifier.weight(1.08f), contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { PlanPanel(state, viewModel) }
                state.result?.let { result -> item { ResultPanel(result) } }
            }
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { CommandCenter(state, viewModel, onChooseDocument) }
            item { PlanPanel(state, viewModel) }
            state.result?.let { result -> item { ResultPanel(result) } }
            item { RecentTasks(recentTasks) }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

@Composable
private fun CommandCenter(state: AgentPadUiState, viewModel: AgentPadViewModel, onChooseDocument: () -> Unit) {
    AgentCard(Modifier.fillMaxWidth(), colors = listOf(Color(0xFF101A20), Color(0xFF0B0F13))) {
        Text("Command Center", style = MaterialTheme.typography.labelLarge, color = ElectricCyan)
        Spacer(Modifier.height(8.dp))
        Text("描述一个目标，让 Agent 先计划、再审批、后执行。", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = state.goal, onValueChange = viewModel::setGoal, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 7, label = { Text("任务目标") }, placeholder = { Text("例如：总结这个文件并给出下一步行动") })
        Spacer(Modifier.height(12.dp))
        if (state.selectedDocument == null) {
            OutlinedButton(onClick = onChooseDocument) {
                Icon(Icons.Rounded.FolderOpen, null)
                Spacer(Modifier.width(8.dp))
                Text("选择文本文件")
            }
        } else {
            SelectedDocumentRow(state, onReplace = onChooseDocument, onRemove = viewModel::clearDocument)
        }
        Spacer(Modifier.height(18.dp))
        Button(onClick = viewModel::createPlan, enabled = !state.busy && state.apiKeyConfigured && state.goal.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            if (state.busy && state.status == TaskStatus.PLANNING) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Graphite) else Icon(Icons.Rounded.AutoAwesome, null)
            Spacer(Modifier.width(8.dp))
            Text("生成计划")
        }
        AnimatedVisibility(state.error != null) { Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp)) }
    }
}

@Composable
private fun SelectedDocumentRow(state: AgentPadUiState, onReplace: () -> Unit, onRemove: () -> Unit) {
    val document = state.selectedDocument ?: return
    Surface(color = Color(0xFF0B1217), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Description, null, tint = ElectricCyan)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(document.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(document.size?.let { "${it / 1024} KB · 等待审批读取" } ?: "等待审批读取", style = MaterialTheme.typography.labelSmall, color = Steel)
            }
            TextButton(onClick = onReplace) { Text("更换") }
            TextButton(onClick = onRemove) { Text("移除") }
        }
    }
}

@Composable
private fun PlanPanel(state: AgentPadUiState, viewModel: AgentPadViewModel) {
    val plan = state.currentPlan
    AgentCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.PendingActions, null, tint = ElectricCyan)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Execution Plan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(plan?.summary ?: "生成计划后，这里会显示步骤、风险和审批状态。", color = Steel, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (plan == null) {
            Spacer(Modifier.height(28.dp))
            EmptyOrbit()
        } else {
            Spacer(Modifier.height(16.dp))
            plan.actions.forEachIndexed { index, action ->
                TimelineAction(index + 1, action, when (viewModel.approvalsFor(plan).firstOrNull { it.first == action.id }?.second) {
                    ApprovalScope.NONE -> true
                    ApprovalScope.TASK -> viewModel.isTaskApproved(state, plan)
                    ApprovalScope.ACTION -> viewModel.isActionApproved(state, plan, action.id)
                    null -> false
                })
                if (index != plan.actions.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 17.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = viewModel::cancelTask, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.StopCircle, null)
                    Spacer(Modifier.width(6.dp))
                    Text("取消")
                }
                Button(onClick = viewModel::executePlan, enabled = !state.busy && state.status != TaskStatus.COMPLETED, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (state.busy) "执行中" else "执行已批准步骤")
                }
            }
            if (state.busy) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun TimelineAction(number: Int, action: PlannedAction, approved: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
        Box(modifier = Modifier.size(34.dp).clip(CircleShape).background(if (approved) Success.copy(alpha = 0.18f) else GraphiteSoft).border(1.dp, if (approved) Success else MaterialTheme.colorScheme.outline, CircleShape), contentAlignment = Alignment.Center) {
            Text(number.toString(), color = if (approved) Success else Mist, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(action.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                RiskBadge(action.risk)
            }
            if (action.description.isNotBlank()) Text(action.description, color = Steel, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            Text(action.tool, color = ElectricCyan, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 5.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ApprovalsScreen(state: AgentPadUiState, viewModel: AgentPadViewModel) {
    val plan = state.currentPlan
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SectionHeading("Approval Queue", "每项敏感操作都说明原因和影响。模型不能降低本地风险级别。") }
        if (plan == null) {
            item { EmptyState("暂时没有待审批任务", "在任务页生成计划后，需要批准的步骤会出现在这里。") }
        } else {
            val scopes = viewModel.approvalsFor(plan).toMap()
            val taskActions = plan.actions.filter { scopes[it.id] == ApprovalScope.TASK }
            if (taskActions.isNotEmpty()) {
                item { ApprovalCard("允许当前任务的普通外部操作", taskActions.joinToString("、") { it.title }, RiskLevel.TASK_APPROVAL, viewModel.isTaskApproved(state, plan), viewModel::approveTask) }
            }
            items(plan.actions.filter { scopes[it.id] == ApprovalScope.ACTION }) { action ->
                ApprovalCard(action.title, action.description.ifBlank { "该操作会影响应用外部或发送数据。" }, action.risk, viewModel.isActionApproved(state, plan, action.id)) { viewModel.approveAction(action.id) }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = {}, label = { Text("计划：${plan.actions.size} 步") }, leadingIcon = { Icon(Icons.Rounded.TaskAlt, null) })
                    AssistChip(onClick = {}, label = { Text("上限：${plan.maxSteps} 步") }, leadingIcon = { Icon(Icons.Rounded.Security, null) })
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ApprovalCard(title: String, description: String, risk: RiskLevel, approved: Boolean, onApprove: () -> Unit) {
    AgentCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (approved) Icons.Rounded.CheckCircle else Icons.Rounded.Security, null, tint = if (approved) Success else riskColor(risk))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text("影响范围：当前任务", color = Steel, style = MaterialTheme.typography.labelSmall)
                Text(description, color = Steel, style = MaterialTheme.typography.bodySmall)
            }
            if (approved) StatusPill("已允许", Success) else FilledTonalButton(onClick = onApprove) { Text("允许本次") }
        }
    }
}

@Composable
private fun CapabilitiesScreen(capabilities: List<CapabilityDescriptor>, apiKeyConfigured: Boolean) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SectionHeading("Capabilities", "这里只展示真实可用状态。未实现或未授权的能力不会伪装成已完成。") }
        items(capabilities) { capability ->
            val effective = if (capability.id == "model" && apiKeyConfigured) capability.copy(state = CapabilityState.AVAILABLE) else capability
            AgentCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(stateColor(effective.state).copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
                        Icon(if (effective.id == "runtime") Icons.Rounded.Memory else Icons.Rounded.Tune, null, tint = stateColor(effective.state))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(effective.name, fontWeight = FontWeight.Bold)
                        Text(effective.description, color = Steel, style = MaterialTheme.typography.bodySmall)
                        Text(effective.enableHint, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                    }
                    StatusPill(stateLabel(effective.state), stateColor(effective.state))
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SettingsScreen(state: AgentPadUiState, viewModel: AgentPadViewModel) {
    val settings = state.providerSettings
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionHeading("Model Setup Wizard", "先用 DeepSeek 快捷配置跑通；需要其它服务时可切换自定义兼容接口。") }
        item {
            AgentCard(Modifier.fillMaxWidth()) {
                Text("Step 1 · 选择服务商", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { viewModel.setProviderSettings(settings.copy(providerId = "deepseek", endpoint = "https://api.deepseek.com/chat/completions", model = "deepseek-chat")) }, modifier = Modifier.weight(1f)) { Text("DeepSeek") }
                    OutlinedButton(onClick = { viewModel.setProviderSettings(settings.copy(providerId = "custom")) }, modifier = Modifier.weight(1f)) { Text("自定义") }
                }
                Spacer(Modifier.height(8.dp))
                Text("当前：${settings.providerId.ifBlank { "未选择" }}", color = Steel, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            AgentCard(Modifier.fillMaxWidth()) {
                Text("Step 2 · 模型与接口", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { viewModel.setProviderSettings(settings.copy(model = "deepseek-chat")) }, modifier = Modifier.weight(1f)) { Text("deepseek-chat") }
                    OutlinedButton(onClick = { viewModel.setProviderSettings(settings.copy(model = "deepseek-reasoner")) }, modifier = Modifier.weight(1f)) { Text("reasoner") }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = settings.endpoint, onValueChange = { viewModel.setProviderSettings(settings.copy(endpoint = it)) }, modifier = Modifier.fillMaxWidth(), label = { Text("接口地址") }, singleLine = true)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = settings.model, onValueChange = { viewModel.setProviderSettings(settings.copy(model = it)) }, modifier = Modifier.fillMaxWidth(), label = { Text("模型名称") }, singleLine = true)
            }
        }
        item {
            AgentCard(Modifier.fillMaxWidth()) {
                Text("Step 3 · 输入 API Key", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = state.apiKeyDraft, onValueChange = viewModel::setApiKeyDraft, modifier = Modifier.fillMaxWidth(), label = { Text(if (state.apiKeyConfigured) "API Key（留空则保持不变）" else "API Key") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                Spacer(Modifier.height(8.dp))
                Text("API Key 只保存在本机 Android Keystore，不进入日志、数据库明文或 Git。", color = Steel, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = viewModel::saveProviderSettings, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("保存配置") }
                    OutlinedButton(onClick = viewModel::testProvider, enabled = !state.busy && state.apiKeyConfigured, modifier = Modifier.weight(1f)) { Text("测试连接") }
                }
                state.error?.let { Text(readableError(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp)) }
                state.result?.let { Text(it, color = Success, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp)) }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun RecentTasks(tasks: List<TaskRecord>) {
    AgentCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.History, null, tint = ElectricCyan)
            Spacer(Modifier.width(10.dp))
            Text("最近任务", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        if (tasks.isEmpty()) Text("完成第一项任务后，执行记录会出现在这里。", color = Steel) else tasks.take(6).forEachIndexed { index, task ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor(task.status)))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(task.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(task.updatedAt)), color = Steel, style = MaterialTheme.typography.labelSmall)
                }
                Text(statusLabel(task.status), color = statusColor(task.status))
            }
            if (index != minOf(tasks.size, 6) - 1) HorizontalDivider(color = GraphiteSoft)
        }
    }
}

@Composable
private fun ResultPanel(result: String) {
    AgentCard(Modifier.fillMaxWidth(), colors = listOf(Color(0xFF10211D), Color(0xFF0E1513))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CheckCircle, null, tint = Success)
            Spacer(Modifier.width(10.dp))
            Text("任务结果", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Text(result, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun EmptyOrbit() {
    val alpha by animateFloatAsState(targetValue = 0.7f, label = "orbit-alpha")
    Box(modifier = Modifier.fillMaxWidth().height(150.dp).drawBehind {
        drawCircle(ElectricCyan.copy(alpha = 0.07f * alpha), radius = size.minDimension * 0.42f)
        drawCircle(ElectricCyan.copy(alpha = 0.22f * alpha), radius = size.minDimension * 0.25f, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
    }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = ElectricCyan, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(8.dp))
            Text("等待任务", color = Steel)
        }
    }
}

@Composable
private fun EmptyState(title: String, body: String) {
    AgentCard(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(body, color = Steel)
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(5.dp))
        Text(subtitle, color = Steel, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AgentCard(modifier: Modifier = Modifier, colors: List<Color> = listOf(GraphiteRaised, Color(0xFF0B1014)), content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
        Column(modifier = Modifier.background(Brush.linearGradient(colors)).border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.52f), RoundedCornerShape(22.dp)).padding(18.dp), content = content)
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.13f), shape = RoundedCornerShape(100.dp)) {
        Text(text, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}

@Composable
private fun RiskBadge(risk: RiskLevel) {
    StatusPill(text = when (risk) {
        RiskLevel.READ_ONLY -> "只读"
        RiskLevel.TASK_APPROVAL -> "任务批准"
        RiskLevel.ACTION_APPROVAL -> "逐项批准"
        RiskLevel.FORBIDDEN -> "禁止"
    }, color = riskColor(risk))
}

private fun readableError(message: String): String = when {
    "401" in message || "key" in message.lowercase() -> "API Key 可能错误，请检查后重新保存。"
    "404" in message || "model" in message.lowercase() -> "模型名称可能不可用，请换一个模型。"
    "timeout" in message.lowercase() || "network" in message.lowercase() -> "网络连接失败或接口无法访问。"
    "endpoint" in message.lowercase() || "url" in message.lowercase() -> "接口地址无法访问，请检查服务商配置。"
    else -> message
}

private fun riskColor(risk: RiskLevel): Color = when (risk) {
    RiskLevel.READ_ONLY -> Success
    RiskLevel.TASK_APPROVAL -> ElectricCyan
    RiskLevel.ACTION_APPROVAL -> Warning
    RiskLevel.FORBIDDEN -> Danger
}

private fun stateColor(state: CapabilityState): Color = when (state) {
    CapabilityState.AVAILABLE -> Success
    CapabilityState.NEEDS_CONFIGURATION -> Warning
    CapabilityState.NEEDS_PERMISSION -> Warning
    CapabilityState.PLANNED -> ElectricCyan
    CapabilityState.UNAVAILABLE -> Danger
}

private fun stateLabel(state: CapabilityState): String = when (state) {
    CapabilityState.AVAILABLE -> "Available"
    CapabilityState.NEEDS_CONFIGURATION -> "Needs setup"
    CapabilityState.NEEDS_PERMISSION -> "Needs permission"
    CapabilityState.PLANNED -> "Planned"
    CapabilityState.UNAVAILABLE -> "Unavailable"
}

private fun statusColor(status: TaskStatus): Color = when (status) {
    TaskStatus.COMPLETED -> Success
    TaskStatus.FAILED -> Danger
    TaskStatus.CANCELLED -> Steel
    TaskStatus.AWAITING_APPROVAL -> Warning
    TaskStatus.RUNNING, TaskStatus.PLANNING, TaskStatus.VERIFYING -> ElectricCyan
    TaskStatus.DRAFT -> Steel
}

private fun statusLabel(status: TaskStatus): String = when (status) {
    TaskStatus.DRAFT -> "待命"
    TaskStatus.PLANNING -> "计划中"
    TaskStatus.AWAITING_APPROVAL -> "待审批"
    TaskStatus.RUNNING -> "执行中"
    TaskStatus.VERIFYING -> "验证中"
    TaskStatus.COMPLETED -> "已完成"
    TaskStatus.FAILED -> "失败"
    TaskStatus.CANCELLED -> "已取消"
}
