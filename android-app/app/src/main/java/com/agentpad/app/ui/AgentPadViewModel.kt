package com.agentpad.app.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.agentpad.app.AgentPadApplication
import com.agentpad.app.domain.ApprovalScope
import com.agentpad.app.domain.ApprovalToken
import com.agentpad.app.domain.CapabilityDescriptor
import com.agentpad.app.domain.CapabilityState
import com.agentpad.app.domain.ProviderSettings
import com.agentpad.app.domain.RiskLevel
import com.agentpad.app.domain.TaskPlan
import com.agentpad.app.domain.TaskRecord
import com.agentpad.app.domain.TaskStatus
import com.agentpad.app.domain.ToolResult
import com.agentpad.app.policy.ApprovalPolicy
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppSection {
    TASKS,
    APPROVALS,
    CAPABILITIES,
    SETTINGS
}

data class SelectedDocument(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val size: Long?
)

data class AgentPadUiState(
    val section: AppSection = AppSection.TASKS,
    val goal: String = "",
    val selectedDocument: SelectedDocument? = null,
    val providerSettings: ProviderSettings = ProviderSettings(),
    val apiKeyConfigured: Boolean = false,
    val apiKeyDraft: String = "",
    val currentPlan: TaskPlan? = null,
    val approvalTokens: Map<String, ApprovalToken> = emptyMap(),
    val status: TaskStatus = TaskStatus.DRAFT,
    val result: String? = null,
    val error: String? = null,
    val busy: Boolean = false
)

class AgentPadViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AgentPadApplication
    private val policy: ApprovalPolicy = app.approvalPolicy
    private val _uiState = MutableStateFlow(
        AgentPadUiState(apiKeyConfigured = app.secureApiKeyStore.hasKey())
    )
    val uiState: StateFlow<AgentPadUiState> = _uiState.asStateFlow()
    val recentTasks: StateFlow<List<TaskRecord>> = app.repository.observeRecentTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val capabilities = listOf(
        CapabilityDescriptor(
            "model",
            "国内模型",
            "DeepSeek 或自定义 OpenAI-compatible 接口",
            CapabilityState.NEEDS_CONFIGURATION,
            RiskLevel.READ_ONLY,
            "在设置中填写模型和 API Key"
        ),
        CapabilityDescriptor(
            "documents",
            "授权文件",
            "通过系统文件选择器读取单个文本文件",
            CapabilityState.AVAILABLE,
            RiskLevel.READ_ONLY,
            "每个文件由用户明确选择"
        ),
        CapabilityDescriptor(
            "intents",
            "系统操作",
            "打开网页、启动已知应用和系统分享",
            CapabilityState.AVAILABLE,
            RiskLevel.TASK_APPROVAL,
            "执行前显示计划并等待批准"
        ),
        CapabilityDescriptor(
            "accessibility",
            "跨应用操作",
            "观察、点击、输入和滑动",
            CapabilityState.PLANNED,
            RiskLevel.ACTION_APPROVAL,
            "计划在 v0.3.0-alpha 按需启用"
        ),
        CapabilityDescriptor(
            "runtime",
            "开发运行时",
            "Python、Git 和受限 Shell",
            CapabilityState.PLANNED,
            RiskLevel.ACTION_APPROVAL,
            "计划通过独立签名 Runtime APK 提供"
        )
    )

    init {
        viewModelScope.launch {
            app.settingsStore.providerSettings.collect { settings ->
                _uiState.update {
                    it.copy(
                        providerSettings = settings,
                        apiKeyConfigured = app.secureApiKeyStore.hasKey()
                    )
                }
            }
        }
    }

    fun setSection(section: AppSection) {
        _uiState.update { it.copy(section = section, error = null) }
    }

    fun setGoal(goal: String) {
        _uiState.update { it.copy(goal = goal.take(2_000), error = null) }
    }

    fun setProviderSettings(settings: ProviderSettings) {
        _uiState.update { it.copy(providerSettings = settings, error = null) }
    }

    fun setApiKeyDraft(value: String) {
        _uiState.update { it.copy(apiKeyDraft = value.take(512), error = null) }
    }

    fun saveProviderSettings() {
        val state = _uiState.value
        viewModelScope.launch {
            runCatching {
                validateSettings(state.providerSettings)
                app.settingsStore.save(state.providerSettings)
                if (state.apiKeyDraft.isNotBlank()) {
                    app.secureApiKeyStore.save(state.apiKeyDraft)
                }
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        apiKeyConfigured = app.secureApiKeyStore.hasKey(),
                        apiKeyDraft = "",
                        error = null
                    )
                }
            }.onFailure { failure ->
                _uiState.update { it.copy(error = failure.safeMessage()) }
            }
        }
    }

    fun testProvider() {
        val state = _uiState.value
        viewModelScope.launch {
            setBusy(true)
            runCatching {
                val key = requireApiKey()
                app.providerClient.test(state.providerSettings, key)
            }.onSuccess { reply ->
                _uiState.update {
                    it.copy(
                        result = if ("AGENTPAD_OK" in reply.uppercase()) {
                            "模型连接成功"
                        } else {
                            "模型已响应：${reply.take(120)}"
                        },
                        error = null
                    )
                }
            }.onFailure { failure ->
                _uiState.update { it.copy(error = failure.safeMessage()) }
            }
            setBusy(false)
        }
    }

    fun selectDocument(uri: Uri) {
        viewModelScope.launch {
            val document = withContext(Dispatchers.IO) { describeDocument(uri) }
            _uiState.update {
                it.copy(
                    selectedDocument = document,
                    currentPlan = null,
                    approvalTokens = emptyMap(),
                    result = null,
                    error = null
                )
            }
        }
    }

    fun clearDocument() {
        _uiState.update { it.copy(selectedDocument = null, currentPlan = null) }
    }

    fun createPlan() {
        val state = _uiState.value
        if (state.goal.isBlank()) {
            _uiState.update { it.copy(error = "请先写清楚希望 Agent 完成的任务") }
            return
        }
        viewModelScope.launch {
            setBusy(true, TaskStatus.PLANNING)
            runCatching {
                val key = requireApiKey()
                app.providerClient.createPlan(
                    goal = state.goal,
                    selectedDocumentName = state.selectedDocument?.name,
                    availableTools = app.toolExecutor.availableTools,
                    settings = state.providerSettings,
                    apiKey = key
                )
            }.onSuccess { plan ->
                app.repository.savePlan(plan)
                _uiState.update {
                    it.copy(
                        currentPlan = plan,
                        status = TaskStatus.AWAITING_APPROVAL,
                        approvalTokens = emptyMap(),
                        result = null,
                        error = null
                    )
                }
            }.onFailure { failure ->
                _uiState.update {
                    it.copy(status = TaskStatus.FAILED, error = failure.safeMessage())
                }
            }
            setBusy(false)
        }
    }

    fun approveTask() {
        val plan = _uiState.value.currentPlan ?: return
        val token = ApprovalToken(
            taskId = plan.id,
            actionId = null,
            argumentDigest = taskApprovalDigest(plan),
            scope = ApprovalScope.TASK,
            expiresAt = System.currentTimeMillis() + APPROVAL_TTL_MILLIS,
            remainingUses = 1
        )
        _uiState.update {
            it.copy(approvalTokens = it.approvalTokens + (taskTokenKey(plan.id) to token), error = null)
        }
    }

    fun approveAction(actionId: String) {
        val plan = _uiState.value.currentPlan ?: return
        val action = plan.actions.firstOrNull { it.id == actionId } ?: return
        val token = ApprovalToken(
            taskId = plan.id,
            actionId = action.id,
            argumentDigest = policy.argumentDigest(action),
            scope = ApprovalScope.ACTION,
            expiresAt = System.currentTimeMillis() + APPROVAL_TTL_MILLIS,
            remainingUses = 1
        )
        _uiState.update {
            it.copy(approvalTokens = it.approvalTokens + (action.id to token), error = null)
        }
    }

    fun cancelTask() {
        val plan = _uiState.value.currentPlan
        viewModelScope.launch {
            if (plan != null) app.repository.updateStatus(plan, TaskStatus.CANCELLED)
            _uiState.update {
                it.copy(
                    status = TaskStatus.CANCELLED,
                    currentPlan = null,
                    approvalTokens = emptyMap(),
                    busy = false
                )
            }
        }
    }

    fun executePlan() {
        val state = _uiState.value
        val plan = state.currentPlan ?: return
        val missing = missingApprovals(state, plan)
        if (missing.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    section = AppSection.APPROVALS,
                    error = "还有 ${missing.size} 项操作需要批准"
                )
            }
            return
        }
        viewModelScope.launch {
            setBusy(true, TaskStatus.RUNNING)
            app.repository.updateStatus(plan, TaskStatus.RUNNING)
            runCatching {
                executeActions(plan)
            }.onSuccess { result ->
                app.repository.updateStatus(plan, TaskStatus.COMPLETED, result)
                _uiState.update {
                    it.copy(
                        status = TaskStatus.COMPLETED,
                        result = result,
                        approvalTokens = emptyMap(),
                        error = null
                    )
                }
            }.onFailure { failure ->
                app.repository.updateStatus(plan, TaskStatus.FAILED, failure.safeMessage())
                _uiState.update {
                    it.copy(status = TaskStatus.FAILED, error = failure.safeMessage())
                }
            }
            setBusy(false)
        }
    }

    fun approvalsFor(plan: TaskPlan): List<Pair<String, ApprovalScope>> =
        plan.actions.map { it.id to policy.requiredScope(it) }

    fun isTaskApproved(state: AgentPadUiState, plan: TaskPlan): Boolean {
        val token = state.approvalTokens[taskTokenKey(plan.id)] ?: return false
        return token.taskId == plan.id &&
            token.scope == ApprovalScope.TASK &&
            token.argumentDigest == taskApprovalDigest(plan) &&
            token.expiresAt >= System.currentTimeMillis() &&
            token.remainingUses > 0
    }

    fun isActionApproved(
        state: AgentPadUiState,
        plan: TaskPlan,
        actionId: String
    ): Boolean {
        val action = plan.actions.firstOrNull { it.id == actionId } ?: return false
        val token = state.approvalTokens[actionId] ?: return false
        return token.taskId == plan.id &&
            token.actionId == action.id &&
            token.scope == ApprovalScope.ACTION &&
            token.argumentDigest == policy.argumentDigest(action) &&
            token.expiresAt >= System.currentTimeMillis() &&
            token.remainingUses > 0
    }

    private suspend fun executeActions(plan: TaskPlan): String {
        var finalResult = "任务步骤已完成"
        for (action in plan.actions.take(plan.maxSteps)) {
            val normalized = policy.normalize(action)
            require(normalized.risk != RiskLevel.FORBIDDEN) { "计划包含永久禁止的操作" }
            val result = when (normalized.tool) {
                "read_document_metadata" -> {
                    val doc = requireDocument()
                    ToolResult(
                        normalized.id,
                        true,
                        "已读取文件元数据",
                        "${doc.name} · ${doc.size ?: 0} bytes"
                    )
                }
                "read_document" -> {
                    val doc = requireDocument()
                    val content = readDocument(doc.uri)
                    ToolResult(
                        normalized.id,
                        true,
                        "已在本机读取文件",
                        "${doc.name} · ${content.length} 字符"
                    )
                }
                "upload_document_for_summary" -> {
                    val doc = requireDocument()
                    val content = readDocument(doc.uri)
                    val summary = app.providerClient.summarizeDocument(
                        goal = plan.goal,
                        documentName = doc.name,
                        content = content,
                        settings = _uiState.value.providerSettings,
                        apiKey = requireApiKey()
                    )
                    finalResult = summary
                    ToolResult(normalized.id, true, "文档总结已完成", "模型返回 ${summary.length} 字符")
                }
                else -> app.toolExecutor.executeIntentAction(normalized)
            }
            app.repository.audit(
                taskId = plan.id,
                actionId = normalized.id,
                eventType = if (result.success) "TOOL_SUCCEEDED" else "TOOL_FAILED",
                summary = result.summary
            )
            if (!result.success) error(result.summary)
        }
        _uiState.update { it.copy(status = TaskStatus.VERIFYING) }
        app.repository.updateStatus(plan, TaskStatus.VERIFYING)
        return finalResult
    }

    private fun missingApprovals(state: AgentPadUiState, plan: TaskPlan): List<String> =
        plan.actions.mapNotNull { action ->
            when (policy.requiredScope(action)) {
                ApprovalScope.NONE -> null
                ApprovalScope.TASK -> if (isTaskApproved(state, plan)) null else action.id
                ApprovalScope.ACTION -> if (isActionApproved(state, plan, action.id)) null else action.id
            }
        }

    private fun taskApprovalDigest(plan: TaskPlan): String =
        plan.actions
            .filter { policy.requiredScope(it) == ApprovalScope.TASK }
            .joinToString("|") { policy.argumentDigest(it) }

    private fun taskTokenKey(taskId: String): String = "task:$taskId"

    private fun requireDocument(): SelectedDocument =
        _uiState.value.selectedDocument ?: error("计划需要文件，但用户尚未选择")

    private fun requireApiKey(): String =
        app.secureApiKeyStore.read() ?: error("尚未配置 API Key")

    private fun validateSettings(settings: ProviderSettings) {
        val endpoint = java.net.URI(settings.endpoint)
        val local = endpoint.host in setOf("127.0.0.1", "localhost", "::1")
        require(endpoint.scheme == "https" || (endpoint.scheme == "http" && local)) {
            "接口必须使用 HTTPS；仅本机回环地址允许 HTTP"
        }
        require(settings.model.isNotBlank()) { "模型名称不能为空" }
    }

    private fun describeDocument(uri: Uri): SelectedDocument {
        val resolver = getApplication<Application>().contentResolver
        var name = "已选择的文件"
        var size: Long? = null
        val mimeType = resolver.getType(uri).orEmpty().lowercase()
        require(
            mimeType.startsWith("text/") ||
                mimeType in setOf("application/json", "application/xml", "application/octet-stream")
        ) {
            "阶段一只支持文本、JSON 和 XML 文件"
        }
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    name = cursor.getString(0) ?: name
                    if (!cursor.isNull(1)) size = cursor.getLong(1)
                }
            }
        require((size ?: 0L) <= MAX_DOCUMENT_BYTES) { "阶段一只读取 1 MB 以内的文本文件" }
        return SelectedDocument(uri, name.take(200), mimeType, size)
    }

    private suspend fun readDocument(uri: Uri): String = withContext(Dispatchers.IO) {
        val resolver = getApplication<Application>().contentResolver
        resolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8_192)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                require(total <= MAX_DOCUMENT_BYTES) { "文件超过阶段一的 1 MB 限制" }
                output.write(buffer, 0, read)
            }
            output.toString(Charsets.UTF_8.name())
        } ?: error("无法读取所选文件")
    }

    private fun setBusy(busy: Boolean, status: TaskStatus? = null) {
        _uiState.update { current ->
            current.copy(
                busy = busy,
                status = status ?: current.status,
                error = if (busy) null else current.error
            )
        }
    }

    private fun Throwable.safeMessage(): String {
        val value = message.orEmpty()
            .replace(Regex("""sk-[A-Za-z0-9_-]{8,}"""), "***REDACTED***")
            .take(500)
        return value.ifBlank { "操作失败，请稍后重试" }
    }

    companion object {
        private const val MAX_DOCUMENT_BYTES = 1024 * 1024
        private const val APPROVAL_TTL_MILLIS = 15 * 60 * 1000L

        fun factory(app: AgentPadApplication): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AgentPadViewModel(app) as T
                }
            }
    }
}
