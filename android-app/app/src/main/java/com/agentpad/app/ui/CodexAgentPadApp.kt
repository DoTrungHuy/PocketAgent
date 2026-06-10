package com.agentpad.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PendingActions
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agentpad.app.domain.ApprovalScope
import com.agentpad.app.domain.TaskRecord
import com.agentpad.app.domain.TaskStatus
import com.agentpad.app.ui.theme.ElectricCyan
import com.agentpad.app.ui.theme.Graphite
import com.agentpad.app.ui.theme.GraphiteRaised
import com.agentpad.app.ui.theme.Mist
import com.agentpad.app.ui.theme.Steel
import com.agentpad.app.ui.theme.Success
import com.agentpad.app.ui.theme.Warning

private data class CodexNavItem(val section: AppSection, val label: String, val icon: ImageVector)

private val codexNav = listOf(
    CodexNavItem(AppSection.TASKS, "Threads", Icons.Rounded.AutoAwesome),
    CodexNavItem(AppSection.APPROVALS, "Approvals", Icons.Rounded.PendingActions),
    CodexNavItem(AppSection.CAPABILITIES, "Tools", Icons.Rounded.Memory),
    CodexNavItem(AppSection.SETTINGS, "Settings", Icons.Rounded.Settings)
)

@Composable
fun CodexAgentPadRoot(viewModel: AgentPadViewModel, onChooseDocument: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val recentTasks by viewModel.recentTasks.collectAsStateWithLifecycle()
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(Graphite).windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val tablet = maxWidth >= 840.dp
        if (tablet) {
            Row(Modifier.fillMaxSize()) {
                CodexSidebar(state, recentTasks, viewModel)
                CodexMain(state, tablet = true, viewModel = viewModel, onChooseDocument = onChooseDocument, modifier = Modifier.weight(1f))
            }
        } else {
            Scaffold(
                containerColor = Graphite,
                bottomBar = { CodexBottomBar(state.section, viewModel::setSection) }
            ) { padding ->
                CodexMain(state, tablet = false, viewModel = viewModel, onChooseDocument = onChooseDocument, modifier = Modifier.padding(padding))
            }
        }
    }
}

@Composable
private fun CodexSidebar(state: AgentPadUiState, recentTasks: List<TaskRecord>, viewModel: AgentPadViewModel) {
    Column(
        Modifier.width(292.dp).fillMaxHeight().background(Color(0xFFF1EDE5)).border(1.dp, Color(0xFFD7D0C4)).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(ElectricCyan), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("AgentPad", color = Mist, fontWeight = FontWeight.Bold)
                Text("Codex-style workspace", color = Steel)
            }
        }
        Spacer(Modifier.height(18.dp))
        Button(onClick = { viewModel.setSection(AppSection.TASKS) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("New task")
        }
        Spacer(Modifier.height(18.dp))
        Text("Navigate", color = Steel, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        codexNav.forEach { item ->
            val selected = state.section == item.section
            Surface(
                onClick = { viewModel.setSection(item.section) },
                color = if (selected) GraphiteRaised else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
                border = if (selected) BorderStroke(1.dp, Color(0xFFD7D0C4)) else null,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(item.icon, null, tint = if (selected) ElectricCyan else Steel)
                    Spacer(Modifier.width(10.dp))
                    Text(item.label, color = Mist, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("Recent", color = Steel, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (recentTasks.isEmpty()) {
            Text("No threads yet", color = Steel)
        } else {
            recentTasks.take(5).forEach { task -> RecentThread(task) }
        }
        Spacer(Modifier.weight(1f))
        if (state.apiKeyConfigured) StatusPill("模型已连接", Success) else OutlinedButton(onClick = { viewModel.setSection(AppSection.SETTINGS) }, modifier = Modifier.fillMaxWidth()) { Text("配置模型") }
    }
}

@Composable
private fun RecentThread(task: TaskRecord) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor(task.status)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(task.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Mist)
            Text(statusLabel(task.status), color = Steel)
        }
    }
}

@Composable
private fun CodexMain(state: AgentPadUiState, tablet: Boolean, viewModel: AgentPadViewModel, onChooseDocument: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(Graphite)) {
        Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Workspace", color = Mist, fontWeight = FontWeight.Bold, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
                Text("Current thread · ${statusLabel(state.status)}", color = Steel)
            }
            if (state.apiKeyConfigured) StatusPill("模型已连接", Success) else Button(onClick = { viewModel.setSection(AppSection.SETTINGS) }) { Text("配置模型") }
        }
        when (state.section) {
            AppSection.TASKS -> if (state.apiKeyConfigured) ThreadScreen(state, tablet, viewModel, onChooseDocument) else WelcomePanel { viewModel.setSection(AppSection.SETTINGS) }
            AppSection.APPROVALS -> SimplePage("Approvals", "待审批操作会显示在这里。")
            AppSection.CAPABILITIES -> SimplePage("Tools", "设备能力和工具状态会显示在这里。")
            AppSection.SETTINGS -> ModelSetupPage(state, viewModel)
        }
    }
}

@Composable
private fun ThreadScreen(state: AgentPadUiState, tablet: Boolean, viewModel: AgentPadViewModel, onChooseDocument: () -> Unit) {
    if (tablet) {
        Row(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            ThreadColumn(state, viewModel, onChooseDocument, Modifier.weight(1.15f), fillPanel = true)
            InspectorColumn(state, viewModel, Modifier.weight(0.85f).fillMaxHeight())
        }
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { ThreadColumn(state, viewModel, onChooseDocument, Modifier.fillMaxWidth(), fillPanel = false) }
            item { InspectorColumn(state, viewModel, Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun ThreadColumn(state: AgentPadUiState, viewModel: AgentPadViewModel, onChooseDocument: () -> Unit, modifier: Modifier, fillPanel: Boolean) {
    Column(modifier) {
        CardPanel(if (fillPanel) Modifier.weight(1f) else Modifier.fillMaxWidth().height(280.dp)) {
            Text("Thread", color = Steel, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Bubble(state.goal.ifBlank { "告诉 AgentPad 一个目标，它会先生成计划。" }, muted = true)
            state.currentPlan?.let { Bubble("已生成执行计划，共 ${it.actions.size} 步。请在右侧检查风险和审批状态。") }
            state.result?.let { Bubble(it) }
        }
        Spacer(Modifier.height(12.dp))
        ComposerPanel(state, viewModel, onChooseDocument)
    }
}

@Composable
private fun ComposerPanel(state: AgentPadUiState, viewModel: AgentPadViewModel, onChooseDocument: () -> Unit) {
    CardPanel {
        OutlinedTextField(
            value = state.goal,
            onValueChange = viewModel::setGoal,
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 5,
            label = { Text("Tell AgentPad what to do") },
            placeholder = { Text("例如：总结这个文件并给出下一步行动") }
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onChooseDocument) {
                Icon(Icons.Rounded.FolderOpen, null)
                Spacer(Modifier.width(8.dp))
                Text(if (state.selectedDocument == null) "Attach file" else "Replace file")
            }
            state.selectedDocument?.let { Text(it.name, color = Steel, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Button(onClick = viewModel::createPlan, enabled = !state.busy && state.goal.isNotBlank()) { Text("Plan") }
        }
    }
}

@Composable
private fun InspectorColumn(state: AgentPadUiState, viewModel: AgentPadViewModel, modifier: Modifier) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PlanPanel(state, viewModel) }
        item { OutputPanel(state.result) }
    }
}

@Composable
private fun PlanPanel(state: AgentPadUiState, viewModel: AgentPadViewModel) {
    val plan = state.currentPlan
    CardPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.PendingActions, null, tint = ElectricCyan)
            Spacer(Modifier.width(10.dp))
            Text("Plan", color = Mist, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        if (plan == null) {
            Text("No plan yet", color = Steel)
        } else {
            plan.actions.forEachIndexed { index, action ->
                val approved = when (viewModel.approvalsFor(plan).firstOrNull { it.first == action.id }?.second) {
                    ApprovalScope.NONE -> true
                    ApprovalScope.TASK -> viewModel.isTaskApproved(state, plan)
                    ApprovalScope.ACTION -> viewModel.isActionApproved(state, plan, action.id)
                    null -> false
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(if (approved) Color(0xFFDFF6E8) else Color(0xFFEDE8DE)), contentAlignment = Alignment.Center) {
                        Text((index + 1).toString(), color = if (approved) Success else Steel, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(action.title, color = Mist, fontWeight = FontWeight.Bold)
                        Text(action.description, color = Steel, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = viewModel::cancelTask, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(onClick = viewModel::executePlan, enabled = !state.busy && state.status != TaskStatus.COMPLETED, modifier = Modifier.weight(1f)) { Text("Run") }
            }
        }
    }
}

@Composable
private fun OutputPanel(result: String?) {
    CardPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CheckCircle, null, tint = Success)
            Spacer(Modifier.width(10.dp))
            Text("Output", color = Mist, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Text(result ?: "执行结果会显示在这里。", color = if (result == null) Steel else Mist)
    }
}

@Composable
private fun ModelSetupPage(state: AgentPadUiState, viewModel: AgentPadViewModel) {
    val settings = state.providerSettings
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            CardPanel {
                Text("Model Setup", color = Mist, fontWeight = FontWeight.Bold, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
                Text("选择 DeepSeek 快捷配置，或者使用自定义兼容接口。", color = Steel)
            }
        }
        item {
            CardPanel {
                Text("1. 服务商", color = Mist, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { viewModel.setProviderSettings(settings.copy(providerId = "deepseek", endpoint = "https://api.deepseek.com/chat/completions", model = "deepseek-chat")) }, modifier = Modifier.weight(1f)) { Text("DeepSeek") }
                    OutlinedButton(onClick = { viewModel.setProviderSettings(settings.copy(providerId = "custom")) }, modifier = Modifier.weight(1f)) { Text("自定义") }
                }
            }
        }
        item {
            CardPanel {
                Text("2. 模型", color = Mist, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { viewModel.setProviderSettings(settings.copy(model = "deepseek-chat")) }, modifier = Modifier.weight(1f)) { Text("chat") }
                    OutlinedButton(onClick = { viewModel.setProviderSettings(settings.copy(model = "deepseek-reasoner")) }, modifier = Modifier.weight(1f)) { Text("reasoner") }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(settings.endpoint, { viewModel.setProviderSettings(settings.copy(endpoint = it)) }, Modifier.fillMaxWidth(), label = { Text("接口地址") }, singleLine = true)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(settings.model, { viewModel.setProviderSettings(settings.copy(model = it)) }, Modifier.fillMaxWidth(), label = { Text("模型名称") }, singleLine = true)
            }
        }
        item {
            CardPanel {
                Text("3. API Key", color = Mist, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(state.apiKeyDraft, viewModel::setApiKeyDraft, Modifier.fillMaxWidth(), label = { Text(if (state.apiKeyConfigured) "API Key（留空保持不变）" else "API Key") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = viewModel::saveProviderSettings, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("保存") }
                    OutlinedButton(onClick = viewModel::testProvider, enabled = !state.busy && state.apiKeyConfigured, modifier = Modifier.weight(1f)) { Text("测试连接") }
                }
            }
        }
    }
}

@Composable
private fun WelcomePanel(onStart: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        CardPanel(Modifier.width(560.dp)) {
            Text("Welcome to AgentPad", color = Mist, fontWeight = FontWeight.Bold, style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text("像 Codex 一样，用任务线程组织工作：输入目标、生成计划、审批并执行。", color = Steel)
            Spacer(Modifier.height(18.dp))
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("开始配置模型") }
        }
    }
}

@Composable
private fun SimplePage(title: String, body: String) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp)) {
        item { CardPanel { Text(title, color = Mist, fontWeight = FontWeight.Bold, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall); Spacer(Modifier.height(8.dp)); Text(body, color = Steel) } }
    }
}

@Composable
private fun CodexBottomBar(current: AppSection, onSelect: (AppSection) -> Unit) {
    NavigationBar(containerColor = Color.White, windowInsets = WindowInsets.navigationBars) {
        codexNav.forEach { item -> NavigationBarItem(selected = current == item.section, onClick = { onSelect(item.section) }, icon = { Icon(item.icon, item.label) }, label = { Text(item.label) }) }
    }
}

@Composable
private fun CardPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = GraphiteRaised), border = BorderStroke(1.dp, Color(0xFFD8D0C2))) { Column(Modifier.padding(18.dp), content = content) }
}

@Composable
private fun Bubble(text: String, muted: Boolean = false) {
    Surface(color = if (muted) Color(0xFFF0ECE4) else Color.White, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFFD8D0C2)), modifier = Modifier.padding(bottom = 12.dp)) {
        Text(text, color = Mist, modifier = Modifier.padding(14.dp))
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.10f), shape = RoundedCornerShape(100.dp), border = BorderStroke(1.dp, color.copy(alpha = 0.55f))) {
        Text(text, color = color, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}

private fun statusColor(status: TaskStatus): Color = when (status) {
    TaskStatus.COMPLETED -> Success
    TaskStatus.FAILED -> Color(0xFFB91C1C)
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
