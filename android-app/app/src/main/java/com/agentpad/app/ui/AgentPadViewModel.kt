package com.agentpad.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.agentpad.app.AgentPadApplication
import com.agentpad.app.data.ThemePreference
import com.agentpad.app.domain.AgentErrorKind
import com.agentpad.app.domain.AgentThread
import com.agentpad.app.domain.AgentTurn
import com.agentpad.app.domain.ApprovalScope
import com.agentpad.app.domain.ApprovalToken
import com.agentpad.app.domain.CapabilityDescriptor
import com.agentpad.app.domain.CapabilityState
import com.agentpad.app.domain.ProviderPresets
import com.agentpad.app.domain.ProviderSettings
import com.agentpad.app.domain.RiskLevel
import com.agentpad.app.domain.TaskPlan
import com.agentpad.app.domain.ThreadAttachment
import com.agentpad.app.domain.ThreadMessage
import com.agentpad.app.domain.ThreadSnapshot
import com.agentpad.app.domain.ToolResult
import com.agentpad.app.domain.TurnStatus
import com.agentpad.app.policy.ApprovalPolicy
import com.agentpad.app.policy.ApprovalTokenPolicy
import com.agentpad.app.provider.ProviderAttachment
import com.agentpad.app.provider.ProviderException
import com.agentpad.app.provider.ThreadContextPolicy
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppSection {
    THREAD,
    PLAN,
    APPROVALS,
    CAPABILITIES,
    SETTINGS
}

data class SelectedDocument(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val size: Long?
) {
    fun toAttachment() = ThreadAttachment(
        threadId = "",
        turnId = null,
        uri = uri.toString(),
        name = name,
        mimeType = mimeType,
        size = size
    )
}

data class AgentPadUiState(
    val section: AppSection = AppSection.THREAD,
    val selectedThreadId: String? = null,
    val snapshot: ThreadSnapshot? = null,
    val draftGoal: String = "",
    val selectedDocument: SelectedDocument? = null,
    val providerSettings: ProviderSettings = ProviderSettings(),
    val apiKeyConfigured: Boolean = false,
    val apiKeyDraft: String = "",
    val approvalTokens: Map<String, ApprovalToken> = emptyMap(),
    val theme: ThemePreference = ThemePreference.LIGHT,
    val privacyMode: Boolean = false,
    val crashReportAvailable: Boolean = false,
    val compressionRequired: Boolean = false,
    val deleteConfirmationThreadId: String? = null,
    val streamingText: String? = null,
    val runningStep: String? = null,
    val lastErrorKind: AgentErrorKind = AgentErrorKind.NONE,
    val auditTrail: List<String> = emptyList(),
    val automationPreviewEnabled: Boolean = false,
    val agentAutoReadEnabled: Boolean = true,
    val agentReadImagesEnabled: Boolean = true,
    val resultNotice: String? = null,
    val error: String? = null,
    val busy: Boolean = false
) {
    val currentTurn: AgentTurn?
        get() = snapshot?.turns?.lastOrNull { it.status != TurnStatus.SUPERSEDED }
            ?: snapshot?.turns?.lastOrNull()

    val currentPlan: TaskPlan?
        get() = currentTurn?.plan

    val attachments: List<ThreadAttachment>
        get() = snapshot?.attachments.orEmpty()
}

class AgentPadViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AgentPadApplication
    private val policy: ApprovalPolicy = app.approvalPolicy
    private val tokenPolicy = ApprovalTokenPolicy(policy)
    private val contextPolicy = ThreadContextPolicy()
    private var activeWorkJob: Job? = null
    private val _uiState = MutableStateFlow(
        AgentPadUiState(
            apiKeyConfigured = app.secureApiKeyStore.hasKey(),
            crashReportAvailable = app.crashReporter.hasCrashReport()
        )
    )
    val uiState: StateFlow<AgentPadUiState> = _uiState.asStateFlow()
    val threads: StateFlow<List<AgentThread>> = app.repository.observeThreads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val providerPresets = ProviderPresets.all

    val capabilities = listOf(
        CapabilityDescriptor(
            "model",
            "模型服务",
            "国内服务商预设与自定义 OpenAI-compatible 接口",
            CapabilityState.NEEDS_CONFIGURATION,
            RiskLevel.READ_ONLY,
            "在设置中完成连接测试"
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
            "设备自动化",
            "v0.3 将按需启用观察、点击、输入和滑动，本版只展示入口与安全边界",
            CapabilityState.PLANNED,
            RiskLevel.ACTION_APPROVAL,
            "不会把 Accessibility 工具加入当前可执行工具列表"
        ),
        CapabilityDescriptor(
            "runtime",
            "开发运行时",
            "Python、Git 和受限 Shell",
            CapabilityState.PLANNED,
            RiskLevel.ACTION_APPROVAL,
            "计划由后续独立签名 Runtime 提供"
        )
    )

    init {
        viewModelScope.launch {
            app.repository.interruptActiveTurns()
        }
        viewModelScope.launch {
            app.settingsStore.preferences.collect { preferences ->
                _uiState.update {
                    it.copy(
                        providerSettings = preferences.providerSettings,
                        apiKeyConfigured = app.secureApiKeyStore.hasKey(),
                        theme = preferences.theme,
                        privacyMode = preferences.privacyMode
                    )
                }
            }
        }
    }

    fun setSection(section: AppSection) {
        _uiState.update { it.copy(section = section, error = null) }
    }

    fun newThread() {
        checkCanLeaveActiveTurn() ?: return
        _uiState.update {
            it.copy(
                section = AppSection.THREAD,
                selectedThreadId = null,
                snapshot = null,
                draftGoal = "",
                selectedDocument = null,
                approvalTokens = emptyMap(),
                compressionRequired = false,
                streamingText = null,
                runningStep = null,
                lastErrorKind = AgentErrorKind.NONE,
                resultNotice = null,
                error = null
            )
        }
    }

    fun openThread(threadId: String) {
        if (threadId != _uiState.value.selectedThreadId && checkCanLeaveActiveTurn() == null) {
            return
        }
        viewModelScope.launch {
            val snapshot = app.repository.loadThread(threadId) ?: return@launch
            _uiState.update {
                it.copy(
                    selectedThreadId = threadId,
                    snapshot = snapshot,
                    section = AppSection.THREAD,
                    draftGoal = "",
                    selectedDocument = null,
                    approvalTokens = emptyMap(),
                    compressionRequired = false,
                    streamingText = null,
                    runningStep = null,
                    lastErrorKind = AgentErrorKind.NONE,
                    resultNotice = null,
                    error = null
                )
            }
        }
    }

    fun setDraftGoal(goal: String) {
        _uiState.update {
            it.copy(
                draftGoal = goal.take(MAX_GOAL_CHARS),
                compressionRequired = false,
                error = null
            )
        }
    }

    fun setProviderSettings(settings: ProviderSettings) {
        _uiState.update { it.copy(providerSettings = settings, error = null, resultNotice = null) }
    }

    fun selectProvider(providerId: String) {
        val preset = ProviderPresets.byId(providerId) ?: return
        val current = _uiState.value.providerSettings
        setProviderSettings(
            current.copy(
                providerId = preset.id,
                endpoint = preset.endpoint.ifBlank { current.endpoint },
                model = if (preset.id == "custom") current.model else preset.defaultModel,
                streamingEnabled = current.streamingEnabled && preset.supportsStreaming
            )
        )
    }

    fun selectDeepSeek() {
        selectProvider("deepseek")
    }

    fun selectCustomProvider() {
        selectProvider("custom")
    }

    fun setStreamingEnabled(enabled: Boolean) {
        val settings = _uiState.value.providerSettings
        val supported = ProviderPresets.byId(settings.providerId)?.supportsStreaming ?: false
        setProviderSettings(settings.copy(streamingEnabled = enabled && supported))
    }

    fun setApiKeyDraft(value: String) {
        _uiState.update { it.copy(apiKeyDraft = value.take(512), error = null, resultNotice = null) }
    }

    fun testAndSaveProvider() {
        val state = _uiState.value
        viewModelScope.launch {
            setBusy(true)
            runCatching {
                validateSettings(state.providerSettings)
                val previousSettings = app.settingsStore.preferences.first().providerSettings
                val previousKey = app.secureApiKeyStore.read()
                val candidateKey = state.apiKeyDraft.ifBlank {
                    previousKey ?: error("请输入 API Key")
                }
                app.providerClient.test(state.providerSettings, candidateKey)
                try {
                    if (state.apiKeyDraft.isNotBlank()) {
                        app.secureApiKeyStore.save(candidateKey)
                    }
                    app.settingsStore.saveProvider(state.providerSettings)
                } catch (failure: Throwable) {
                    if (previousKey == null) {
                        app.secureApiKeyStore.clear()
                    } else {
                        app.secureApiKeyStore.save(previousKey)
                    }
                    runCatching { app.settingsStore.saveProvider(previousSettings) }
                    throw failure
                }
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        apiKeyConfigured = true,
                        apiKeyDraft = "",
                        resultNotice = "连接测试成功，配置已加密保存",
                        error = null
                    )
                }
            }.onFailure { failure ->
                _uiState.update {
                    it.copy(
                        error = failure.safeMessage(),
                        lastErrorKind = failure.errorKind(),
                        resultNotice = null
                    )
                }
            }
            setBusy(false)
        }
    }

    fun setTheme(theme: ThemePreference) {
        viewModelScope.launch { app.settingsStore.setTheme(theme) }
    }

    fun setPrivacyMode(enabled: Boolean) {
        viewModelScope.launch { app.settingsStore.setPrivacyMode(enabled) }
    }

    fun recordUiContext(section: AppSection, widthDp: Int) {
        app.crashReporter.recordUiContext(section.name, widthDp)
    }

    fun dismissCrashReport() {
        app.crashReporter.clearCrashReport()
        _uiState.update { it.copy(crashReportAvailable = false) }
    }

    fun selectDocument(uri: Uri) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { describeDocument(uri) } }
                .onSuccess { document ->
                    _uiState.update {
                        it.copy(selectedDocument = document, error = null)
                    }
                }
                .onFailure { failure ->
                    _uiState.update { it.copy(error = failure.safeMessage()) }
                }
        }
    }

    fun clearDocument() {
        _uiState.update { it.copy(selectedDocument = null) }
    }

    fun sendChat() {
        val state = _uiState.value
        if (state.draftGoal.isBlank()) {
            _uiState.update { it.copy(error = "请输入消息") }
            return
        }
        if (!state.apiKeyConfigured) {
            _uiState.update { it.copy(section = AppSection.SETTINGS, error = "请先配置并测试模型") }
            return
        }
        if (hasRunningTurn(state.currentTurn)) {
            _uiState.update { it.copy(error = "当前对话仍在回复，请先等待或取消") }
            return
        }
        val messages = state.snapshot?.messages.orEmpty()
        if (contextPolicy.needsCompression(messages)) {
            _uiState.update { it.copy(compressionRequired = true, error = null) }
            return
        }
        sendChatAfterContextReady()
    }

    fun confirmCompressionAndSendChat() {
        val state = _uiState.value
        val threadId = state.selectedThreadId ?: return
        val history = contextPolicy.requestMessages(state.snapshot?.messages.orEmpty())
        viewModelScope.launch {
            setBusy(true)
            try {
                val summary = app.providerClient.compressContext(
                    history = history,
                    settings = state.providerSettings,
                    apiKey = requireApiKey()
                )
                app.repository.addContextSummary(threadId, summary)
                loadSelectedThread()
                _uiState.update { it.copy(compressionRequired = false) }
                setBusy(false)
                sendChatAfterContextReady()
            } catch (failure: Throwable) {
                _uiState.update {
                    it.copy(
                        error = failure.safeMessage(),
                        lastErrorKind = failure.errorKind(),
                        compressionRequired = false
                    )
                }
                setBusy(false)
            }
        }
    }

    private fun sendChatAfterContextReady() {
        if (activeWorkJob?.isActive == true || _uiState.value.busy) return
        val state = _uiState.value
        val prompt = state.draftGoal.trim()
        val history = contextPolicy.requestMessages(state.snapshot?.messages.orEmpty())
        val selectedDocument = state.selectedDocument
        val attachment = selectedDocument?.toAttachment()
        setBusy(true)
        activeWorkJob = viewModelScope.launch {
            var turn: AgentTurn? = null
            try {
                _uiState.update {
                    it.copy(
                        runningStep = "模型回复",
                        lastErrorKind = AgentErrorKind.NONE,
                        resultNotice = null,
                        error = null
                    )
                }
                val providerAttachments = withContext(Dispatchers.IO) {
                    authorizedProviderAttachments(state, selectedDocument)
                }
                turn = app.repository.beginChatTurn(state.selectedThreadId, prompt, attachment)
                _uiState.update {
                    it.copy(
                        selectedThreadId = turn?.threadId,
                        section = AppSection.THREAD,
                        draftGoal = "",
                        selectedDocument = null
                    )
                }
                loadSelectedThread()
                val reply = app.providerClient.chatReply(
                    prompt = prompt,
                    history = history,
                    attachments = providerAttachments,
                    settings = state.providerSettings,
                    apiKey = requireApiKey()
                )
                app.repository.completeChatTurn(requireNotNull(turn), reply)
                _uiState.update {
                    it.copy(
                        resultNotice = null,
                        runningStep = null,
                        error = null
                    )
                }
                loadSelectedThread()
            } catch (_: CancellationException) {
                // cancelTurn records the terminal state and clears approvals.
            } catch (failure: Throwable) {
                turn?.let {
                    app.repository.failChatTurn(it, failure.safeMessage())
                }
                _uiState.update {
                    it.copy(
                        error = failure.safeMessage(),
                        lastErrorKind = failure.errorKind(),
                        resultNotice = null
                    )
                }
                loadSelectedThread()
            } finally {
                activeWorkJob = null
                setBusy(false)
            }
        }
    }

    fun createPlan() {
        val state = _uiState.value
        if (state.draftGoal.isBlank()) {
            _uiState.update { it.copy(error = "请先写清楚希望 AgentPad 完成的任务") }
            return
        }
        if (!state.apiKeyConfigured) {
            _uiState.update { it.copy(section = AppSection.SETTINGS, error = "请先配置并测试模型") }
            return
        }
        if (hasRunningTurn(state.currentTurn)) {
            _uiState.update { it.copy(error = "当前回合仍在执行，请先完成或取消") }
            return
        }
        val messages = state.snapshot?.messages.orEmpty()
        if (contextPolicy.needsCompression(messages)) {
            _uiState.update { it.copy(compressionRequired = true, error = null) }
            return
        }
        createPlanAfterContextReady()
    }

    fun confirmCompressionAndCreatePlan() {
        val state = _uiState.value
        val threadId = state.selectedThreadId ?: return
        val history = contextPolicy.requestMessages(state.snapshot?.messages.orEmpty())
        viewModelScope.launch {
            setBusy(true)
            try {
                val summary = app.providerClient.compressContext(
                    history = history,
                    settings = state.providerSettings,
                    apiKey = requireApiKey()
                )
                app.repository.addContextSummary(threadId, summary)
                loadSelectedThread()
                _uiState.update { it.copy(compressionRequired = false) }
                setBusy(false)
                createPlanAfterContextReady()
            } catch (failure: Throwable) {
                _uiState.update {
                    it.copy(
                        error = failure.safeMessage(),
                        lastErrorKind = failure.errorKind(),
                        compressionRequired = false
                    )
                }
                setBusy(false)
            }
        }
    }

    fun dismissCompression() {
        _uiState.update { it.copy(compressionRequired = false) }
    }

    private fun createPlanAfterContextReady() {
        if (activeWorkJob?.isActive == true || _uiState.value.busy) return
        val state = _uiState.value
        val goal = state.draftGoal.trim()
        val history = contextPolicy.requestMessages(state.snapshot?.messages.orEmpty())
        val attachment = state.selectedDocument?.toAttachment()
        val attachmentMetadata = state.snapshot?.attachments.orEmpty() + listOfNotNull(attachment)
        setBusy(true)
        activeWorkJob = viewModelScope.launch {
            var turn: AgentTurn? = null
            try {
                _uiState.update {
                    it.copy(
                        streamingText = "正在把目标、线程上下文和附件元数据发送给模型...",
                        runningStep = "生成计划",
                        lastErrorKind = AgentErrorKind.NONE
                    )
                }
                turn = app.repository.beginTurn(state.selectedThreadId, goal, attachment)
                _uiState.update {
                    it.copy(
                        selectedThreadId = turn?.threadId,
                        section = AppSection.THREAD,
                        approvalTokens = emptyMap(),
                        compressionRequired = false
                    )
                }
                loadSelectedThread()
                val plan = app.providerClient.createPlan(
                    goal = goal,
                    history = history,
                    attachments = attachmentMetadata,
                    availableTools = app.toolExecutor.availableTools,
                    settings = state.providerSettings,
                    apiKey = requireApiKey()
                )
                app.repository.savePlan(requireNotNull(turn), plan)
                _uiState.update {
                    it.copy(
                        draftGoal = "",
                        selectedDocument = null,
                        section = AppSection.THREAD,
                        resultNotice = "计划已生成，请检查风险和审批要求",
                        streamingText = null,
                        runningStep = "等待审批",
                        error = null
                    )
                }
                loadSelectedThread()
            } catch (_: CancellationException) {
                // cancelTurn records the terminal state and clears approvals.
            } catch (failure: Throwable) {
                turn?.let {
                    app.repository.updateStatus(it, TurnStatus.FAILED, failure.safeMessage())
                }
                _uiState.update { it.copy(error = failure.safeMessage(), resultNotice = null) }
                _uiState.update { it.copy(lastErrorKind = failure.errorKind(), streamingText = null) }
                loadSelectedThread()
            } finally {
                activeWorkJob = null
                setBusy(false)
            }
        }
    }

    fun approveTask() {
        val plan = _uiState.value.currentPlan ?: return
        val token = tokenPolicy.createTaskToken(
            plan = plan,
            now = System.currentTimeMillis(),
            ttlMillis = APPROVAL_TTL_MILLIS
        )
        _uiState.update {
            it.copy(
                approvalTokens = it.approvalTokens + (tokenPolicy.taskTokenKey(plan.id) to token),
                error = null
            )
        }
    }

    fun approveAction(actionId: String) {
        val plan = _uiState.value.currentPlan ?: return
        val action = plan.actions.firstOrNull { it.id == actionId } ?: return
        val token = tokenPolicy.createActionToken(
            plan = plan,
            action = action,
            now = System.currentTimeMillis(),
            ttlMillis = APPROVAL_TTL_MILLIS
        )
        _uiState.update {
            it.copy(approvalTokens = it.approvalTokens + (action.id to token), error = null)
        }
    }

    fun cancelTurn() {
        val turn = _uiState.value.currentTurn ?: return
        activeWorkJob?.cancel()
        activeWorkJob = null
        viewModelScope.launch {
            app.repository.updateStatus(turn, TurnStatus.CANCELLED)
            _uiState.update {
                it.copy(
                    approvalTokens = emptyMap(),
                    busy = false,
                    streamingText = null,
                    runningStep = null,
                    lastErrorKind = AgentErrorKind.CANCELLED_BY_USER,
                    resultNotice = "当前回合已取消"
                )
            }
            loadSelectedThread()
        }
    }

    fun executePlan() {
        if (activeWorkJob?.isActive == true || _uiState.value.busy) return
        val state = _uiState.value
        val turn = state.currentTurn ?: return
        val plan = turn.plan ?: return
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
        consumeApprovals(state, plan)
        setBusy(true)
        activeWorkJob = viewModelScope.launch {
            var runningTurn = app.repository.updateStatus(turn, TurnStatus.RUNNING)
            loadSelectedThread()
            try {
                _uiState.update {
                    it.copy(
                        streamingText = "正在执行已批准的计划...",
                        runningStep = "执行计划",
                        lastErrorKind = AgentErrorKind.NONE
                    )
                }
                val result = executeActions(runningTurn, plan)
                runningTurn = app.repository.updateStatus(runningTurn, TurnStatus.COMPLETED, result)
                _uiState.update {
                    it.copy(
                        approvalTokens = emptyMap(),
                        section = AppSection.THREAD,
                        resultNotice = "任务已完成",
                        streamingText = null,
                        runningStep = "已完成",
                        error = null
                    )
                }
            } catch (_: CancellationException) {
                // cancelTurn records the terminal state and clears approvals.
            } catch (failure: Throwable) {
                app.repository.updateStatus(runningTurn, TurnStatus.FAILED, failure.safeMessage())
                _uiState.update {
                    it.copy(
                        error = failure.safeMessage(),
                        lastErrorKind = failure.errorKind(),
                        streamingText = null,
                        resultNotice = null
                    )
                }
            } finally {
                loadSelectedThread()
                activeWorkJob = null
                setBusy(false)
            }
        }
    }

    fun approvalsFor(plan: TaskPlan): List<Pair<String, ApprovalScope>> =
        plan.actions.map { it.id to policy.requiredScope(it) }

    fun isTaskApproved(state: AgentPadUiState, plan: TaskPlan): Boolean {
        val token = state.approvalTokens[tokenPolicy.taskTokenKey(plan.id)]
        return tokenPolicy.isTaskValid(token, plan, System.currentTimeMillis())
    }

    fun isActionApproved(
        state: AgentPadUiState,
        plan: TaskPlan,
        actionId: String
    ): Boolean {
        val action = plan.actions.firstOrNull { it.id == actionId } ?: return false
        return tokenPolicy.isActionValid(
            state.approvalTokens[actionId],
            plan,
            action,
            System.currentTimeMillis()
        )
    }

    fun requestDeleteThread(threadId: String) {
        if (threadId == _uiState.value.selectedThreadId && checkCanLeaveActiveTurn() == null) {
            return
        }
        _uiState.update { it.copy(deleteConfirmationThreadId = threadId) }
    }

    fun dismissDeleteThread() {
        _uiState.update { it.copy(deleteConfirmationThreadId = null) }
    }

    fun confirmDeleteThread() {
        val threadId = _uiState.value.deleteConfirmationThreadId ?: return
        viewModelScope.launch {
            val attachments = app.repository.deleteThread(threadId)
            attachments.map { Uri.parse(it.uri) }.distinct().forEach { uri ->
                runCatching {
                    getApplication<Application>().contentResolver.releasePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
            _uiState.update {
                it.copy(
                    selectedThreadId = null,
                    snapshot = null,
                    deleteConfirmationThreadId = null,
                    approvalTokens = emptyMap(),
                    draftGoal = "",
                    resultNotice = "线程已删除"
                )
            }
            threads.value.firstOrNull()?.takeIf { it.id != threadId }?.let { openThread(it.id) }
        }
    }

    fun removeAttachment(attachmentId: String) {
        viewModelScope.launch {
            runCatching {
                app.repository.removeAttachment(attachmentId)?.let { attachment ->
                    getApplication<Application>().contentResolver.releasePersistableUriPermission(
                        Uri.parse(attachment.uri),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }.onSuccess {
                _uiState.update { it.copy(resultNotice = "附件授权已移除", error = null) }
                loadSelectedThread()
            }.onFailure { failure ->
                _uiState.update {
                    it.copy(error = failure.safeMessage(), lastErrorKind = failure.errorKind())
                }
            }
        }
    }

    fun summarizeAttachment(attachmentId: String) {
        val state = _uiState.value
        val attachment = state.attachments.firstOrNull { it.id == attachmentId } ?: return
        val threadId = state.selectedThreadId ?: return
        viewModelScope.launch {
            setBusy(true)
            runCatching {
                _uiState.update {
                    it.copy(
                        streamingText = "正在重新读取并总结附件：${attachment.name}",
                        runningStep = "附件总结",
                        lastErrorKind = AgentErrorKind.NONE
                    )
                }
                val content = readDocument(Uri.parse(attachment.uri))
                val summary = app.providerClient.summarizeDocument(
                    goal = state.currentTurn?.goal ?: state.snapshot?.thread?.title.orEmpty(),
                    documentName = attachment.name,
                    content = content,
                    settings = state.providerSettings,
                    apiKey = requireApiKey()
                )
                app.repository.addAssistantResult(threadId, attachment.turnId, summary)
                app.repository.audit(
                    taskId = attachment.turnId ?: threadId,
                    actionId = attachment.id,
                    eventType = "ATTACHMENT_SUMMARIZED",
                    summary = "附件已重新总结：${attachment.name}"
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(resultNotice = "附件总结已加入聊天记录", error = null)
                }
                loadSelectedThread()
            }.onFailure { failure ->
                _uiState.update {
                    it.copy(error = failure.safeMessage(), lastErrorKind = failure.errorKind())
                }
            }
            setBusy(false)
        }
    }

    fun setAutomationPreviewEnabled(enabled: Boolean) {
        _uiState.update {
            it.copy(
                automationPreviewEnabled = enabled,
                resultNotice = if (enabled) {
                    "设备自动化仍处于 v0.3 设计预览，不会执行跨应用操作"
                } else {
                    null
                },
                error = null
            )
        }
    }

    fun setAgentAutoReadEnabled(enabled: Boolean) {
        _uiState.update { it.copy(agentAutoReadEnabled = enabled, error = null) }
    }

    fun setAgentReadImagesEnabled(enabled: Boolean) {
        _uiState.update { it.copy(agentReadImagesEnabled = enabled, error = null) }
    }

    private suspend fun executeActions(turn: AgentTurn, plan: TaskPlan): String {
        var finalResult = "任务步骤已完成"
        for (action in plan.actions.take(plan.maxSteps)) {
            val normalized = policy.normalize(action)
            require(normalized.risk != RiskLevel.FORBIDDEN) { "计划包含永久禁止的操作" }
            _uiState.update {
                it.copy(
                    runningStep = normalized.title,
                    streamingText = "正在执行：${normalized.title}"
                )
            }
            val result = when (normalized.tool) {
                "read_document_metadata" -> {
                    val doc = requireAttachment(turn)
                    ToolResult(
                        normalized.id,
                        true,
                        "已读取文件元数据",
                        "${doc.name} · ${doc.size ?: 0} bytes"
                    )
                }
                "read_document" -> {
                    val doc = requireAttachment(turn)
                    val content = readDocument(Uri.parse(doc.uri))
                    ToolResult(
                        normalized.id,
                        true,
                        "已在本机读取文件",
                        "${doc.name} · ${content.length} 字符"
                    )
                }
                "upload_document_for_summary" -> {
                    val doc = requireAttachment(turn)
                    val content = readDocument(Uri.parse(doc.uri))
                    val summary = app.providerClient.summarizeDocument(
                        goal = plan.goal,
                        documentName = doc.name,
                        content = content,
                        settings = _uiState.value.providerSettings,
                        apiKey = requireApiKey()
                    )
                    finalResult = summary
                    ToolResult(
                        normalized.id,
                        true,
                        "文档总结已完成",
                        "模型返回 ${summary.length} 字符"
                    )
                }
                else -> app.toolExecutor.executeIntentAction(normalized)
            }
            app.repository.audit(
                taskId = turn.id,
                actionId = normalized.id,
                eventType = if (result.success) "TOOL_SUCCEEDED" else "TOOL_FAILED",
                summary = result.summary + result.evidence?.let { " · $it" }.orEmpty()
            )
            if (!result.success) error(result.summary)
            loadSelectedThread()
        }
        _uiState.update { it.copy(runningStep = "验证结果", streamingText = "正在写入结果和审计记录...") }
        app.repository.updateStatus(turn, TurnStatus.VERIFYING)
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

    private fun consumeApprovals(state: AgentPadUiState, plan: TaskPlan) {
        val consumed = state.approvalTokens.toMutableMap()
        val taskKey = tokenPolicy.taskTokenKey(plan.id)
        consumed[taskKey]?.let { consumed[taskKey] = tokenPolicy.consume(it) }
        plan.actions
            .filter { policy.requiredScope(it) == ApprovalScope.ACTION }
            .forEach { action ->
                consumed[action.id]?.let { consumed[action.id] = tokenPolicy.consume(it) }
            }
        _uiState.update { it.copy(approvalTokens = consumed) }
    }

    private fun requireAttachment(turn: AgentTurn): ThreadAttachment {
        return _uiState.value.snapshot?.attachments
            ?.lastOrNull { it.turnId == turn.id }
            ?: error("计划需要文件，但当前回合没有可用附件")
    }

    private fun requireApiKey(): String =
        app.secureApiKeyStore.read() ?: error("尚未配置 API Key")

    private fun validateSettings(settings: ProviderSettings) {
        require(settings.providerId in ProviderPresets.all.map { it.id }) {
            "请选择支持的模型服务商"
        }
        val endpoint = java.net.URI(settings.endpoint)
        val local = endpoint.host in setOf("127.0.0.1", "localhost", "::1")
        require(endpoint.scheme == "https" || (endpoint.scheme == "http" && local)) {
            "接口必须使用 HTTPS；仅本机回环地址允许 HTTP"
        }
        require(settings.model.isNotBlank()) { "模型名称不能为空" }
    }

    private suspend fun loadSelectedThread() {
        val threadId = _uiState.value.selectedThreadId ?: return
        val snapshot = app.repository.loadThread(threadId) ?: return
        val audit = app.repository.recentAuditSummaries()
        _uiState.update { it.copy(snapshot = snapshot, auditTrail = audit) }
        app.crashReporter.updateAuditSummaries(audit)
    }

    private fun checkCanLeaveActiveTurn(): Unit? {
        if (activeWorkJob?.isActive == true || hasRunningTurn(_uiState.value.currentTurn)) {
            _uiState.update { it.copy(error = "当前回合仍在执行，请先完成或取消") }
            return null
        }
        return Unit
    }

    private fun hasRunningTurn(turn: AgentTurn?): Boolean =
        turn?.status in setOf(TurnStatus.PLANNING, TurnStatus.RUNNING, TurnStatus.VERIFYING)

    private fun describeDocument(uri: Uri): SelectedDocument {
        val resolver = getApplication<Application>().contentResolver
        var name = "已选择的文件"
        var size: Long? = null
        val mimeType = resolver.getType(uri).orEmpty().lowercase()
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    name = cursor.getString(0) ?: name
                    if (!cursor.isNull(1)) size = cursor.getLong(1)
                }
            }
        require((size ?: 0L) <= MAX_FILE_BYTES) { "文件超过当前版本的 8 MB 授权限制" }
        return SelectedDocument(uri, name.take(200), mimeType, size)
    }

    private fun authorizedProviderAttachments(
        state: AgentPadUiState,
        selectedDocument: SelectedDocument?
    ): List<ProviderAttachment> {
        val selectedUri = selectedDocument?.uri?.toString()
        val persisted = if (state.agentAutoReadEnabled) {
            state.attachments
                .asReversed()
                .distinctBy { it.uri }
                .filter { it.uri != selectedUri }
                .take(MAX_AUTO_ATTACHMENTS)
                .asReversed()
                .mapNotNull { attachment ->
                    runCatching { readProviderAttachment(attachment, state.agentReadImagesEnabled) }.getOrNull()
                }
        } else {
            emptyList()
        }
        val selected = selectedDocument?.let { readProviderAttachment(it, state.agentReadImagesEnabled) }
        return (persisted + listOfNotNull(selected)).takeLast(MAX_AUTO_ATTACHMENTS + 1)
    }

    private fun readProviderAttachment(
        document: SelectedDocument,
        includeImages: Boolean
    ): ProviderAttachment = readProviderAttachment(
        uri = document.uri,
        name = document.name,
        mimeType = document.mimeType,
        size = document.size,
        includeImages = includeImages
    )

    private fun readProviderAttachment(
        attachment: ThreadAttachment,
        includeImages: Boolean
    ): ProviderAttachment = readProviderAttachment(
        uri = Uri.parse(attachment.uri),
        name = attachment.name,
        mimeType = attachment.mimeType,
        size = attachment.size,
        includeImages = includeImages
    )

    private fun readProviderAttachment(
        uri: Uri,
        name: String,
        mimeType: String,
        size: Long?,
        includeImages: Boolean
    ): ProviderAttachment {
        val normalizedType = mimeType.lowercase()
        return when {
            isReadableText(normalizedType, name) -> ProviderAttachment(
                name = name,
                mimeType = normalizedType,
                size = size,
                text = readDocument(uri)
            )
            includeImages && normalizedType.startsWith("image/") -> ProviderAttachment(
                name = name,
                mimeType = normalizedType,
                size = size,
                imageDataUri = readImageDataUri(uri, normalizedType)
            )
            else -> ProviderAttachment(name = name, mimeType = normalizedType, size = size)
        }
    }

    private fun isReadableText(mimeType: String, name: String): Boolean =
        mimeType.startsWith("text/") ||
            mimeType in setOf(
                "application/json",
                "application/xml",
                "application/javascript",
                "application/octet-stream"
            ) ||
            name.endsWith(".md", ignoreCase = true) ||
            name.endsWith(".txt", ignoreCase = true) ||
            name.endsWith(".json", ignoreCase = true) ||
            name.endsWith(".xml", ignoreCase = true)

    private fun readDocument(uri: Uri): String {
        val resolver = getApplication<Application>().contentResolver
        return resolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8_192)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                require(total <= MAX_DOCUMENT_BYTES) { "文件超过当前版本的 1 MB 限制" }
                output.write(buffer, 0, read)
            }
            output.toString(Charsets.UTF_8.name())
        } ?: error("无法读取所选文件")
    }

    private fun readImageDataUri(uri: Uri, mimeType: String): String {
        val resolver = getApplication<Application>().contentResolver
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8_192)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                require(total <= MAX_IMAGE_BYTES) { "图片超过当前版本的 6 MB 限制" }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        } ?: error("无法读取所选图片")
        return "data:$mimeType;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
    }

    private fun setBusy(busy: Boolean) {
        _uiState.update {
            it.copy(
                busy = busy,
                error = if (busy) null else it.error,
                streamingText = if (busy) it.streamingText else null,
                runningStep = if (busy) it.runningStep else null
            )
        }
    }

    private fun Throwable.safeMessage(): String {
        val value = message.orEmpty()
            .replace(Regex("""sk-[A-Za-z0-9_-]{8,}"""), "***REDACTED***")
            .take(500)
        return value.ifBlank { "操作失败，请稍后重试" }
    }

    private fun Throwable.errorKind(): AgentErrorKind = when (this) {
        is ProviderException -> kind
        is CancellationException -> AgentErrorKind.CANCELLED_BY_USER
        is java.net.SocketTimeoutException -> AgentErrorKind.NETWORK_TIMEOUT
        else -> AgentErrorKind.LOCAL_FAILURE
    }

    companion object {
        private const val MAX_DOCUMENT_BYTES = 1024 * 1024
        private const val MAX_IMAGE_BYTES = 6 * 1024 * 1024
        private const val MAX_FILE_BYTES = 8 * 1024 * 1024
        private const val MAX_AUTO_ATTACHMENTS = 6
        private const val MAX_GOAL_CHARS = 4_000
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
