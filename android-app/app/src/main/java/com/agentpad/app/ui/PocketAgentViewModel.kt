package com.agentpad.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.agentpad.app.PocketAgentApplication
import com.agentpad.app.data.ThemePreference
import com.agentpad.app.document.DocumentExtractor
import com.agentpad.app.document.DocumentSearchEngine
import com.agentpad.app.domain.AgentErrorKind
import com.agentpad.app.domain.AgentThread
import com.agentpad.app.domain.AgentTurn
import com.agentpad.app.domain.DocumentAccessAction
import com.agentpad.app.domain.DocumentGrant
import com.agentpad.app.domain.DocumentSearchRequest
import com.agentpad.app.domain.DocumentSearchResult
import com.agentpad.app.domain.DocumentSearchStage
import com.agentpad.app.domain.ProviderPresets
import com.agentpad.app.domain.ProviderSettings
import com.agentpad.app.domain.ThreadAttachment
import com.agentpad.app.domain.ThreadMessage
import com.agentpad.app.domain.ThreadSnapshot
import com.agentpad.app.domain.TurnStatus
import com.agentpad.app.provider.ProviderException
import com.agentpad.app.provider.ThreadContextPolicy
import java.net.URI
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
    SETTINGS
}

data class PocketAgentUiState(
    val section: AppSection = AppSection.THREAD,
    val selectedThreadId: String? = null,
    val snapshot: ThreadSnapshot? = null,
    val draftGoal: String = "",
    val providerSettings: ProviderSettings = ProviderSettings(),
    val apiKeyConfigured: Boolean = false,
    val apiKeyDraft: String = "",
    val theme: ThemePreference = ThemePreference.LIGHT,
    val privacyMode: Boolean = false,
    val crashReportAvailable: Boolean = false,
    val deleteConfirmationThreadId: String? = null,
    val documentGrants: List<DocumentGrant> = emptyList(),
    val documentSearchRequest: DocumentSearchRequest? = null,
    val documentSearchResults: List<DocumentSearchResult> = emptyList(),
    val runningStep: String? = null,
    val lastErrorKind: AgentErrorKind = AgentErrorKind.NONE,
    val resultNotice: String? = null,
    val error: String? = null,
    val busy: Boolean = false
) {
    val currentTurn: AgentTurn?
        get() = snapshot?.turns?.lastOrNull { it.status != TurnStatus.SUPERSEDED }
            ?: snapshot?.turns?.lastOrNull()

    val attachments: List<ThreadAttachment>
        get() = snapshot?.attachments.orEmpty()
}

class PocketAgentViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PocketAgentApplication
    private val contextPolicy = ThreadContextPolicy()
    private val extractor = DocumentExtractor(application)
    private val searchEngine = DocumentSearchEngine()
    private var activeWorkJob: Job? = null

    private val _uiState = MutableStateFlow(
        PocketAgentUiState(
            apiKeyConfigured = app.secureApiKeyStore.hasKey(),
            crashReportAvailable = app.crashReporter.hasCrashReport()
        )
    )
    val uiState: StateFlow<PocketAgentUiState> = _uiState.asStateFlow()
    val threads: StateFlow<List<AgentThread>> = app.repository.observeThreads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val providerPresets = ProviderPresets.all

    init {
        viewModelScope.launch { app.repository.interruptActiveTurns() }
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
        viewModelScope.launch { refreshDocumentGrants() }
    }

    fun setSection(section: AppSection) {
        _uiState.update { it.copy(section = section, error = null) }
    }

    fun newThread() {
        if (!canLeaveActiveTurn()) return
        _uiState.update {
            it.copy(
                section = AppSection.THREAD,
                selectedThreadId = null,
                snapshot = null,
                draftGoal = "",
                documentSearchRequest = null,
                documentSearchResults = emptyList(),
                runningStep = null,
                resultNotice = null,
                error = null,
                lastErrorKind = AgentErrorKind.NONE
            )
        }
    }

    fun openThread(threadId: String) {
        if (threadId != _uiState.value.selectedThreadId && !canLeaveActiveTurn()) return
        viewModelScope.launch {
            val snapshot = app.repository.loadThread(threadId) ?: return@launch
            _uiState.update {
                it.copy(
                    selectedThreadId = threadId,
                    snapshot = snapshot,
                    section = AppSection.THREAD,
                    draftGoal = "",
                    documentSearchRequest = null,
                    documentSearchResults = emptyList(),
                    runningStep = null,
                    resultNotice = null,
                    error = null,
                    lastErrorKind = AgentErrorKind.NONE
                )
            }
        }
    }

    fun setDraftGoal(goal: String) {
        _uiState.update {
            it.copy(
                draftGoal = goal.take(MAX_GOAL_CHARS),
                resultNotice = null,
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

    fun setApiKeyDraft(value: String) {
        _uiState.update { it.copy(apiKeyDraft = value.take(512), error = null, resultNotice = null) }
    }

    fun testAndSaveProvider() {
        val state = _uiState.value
        viewModelScope.launch {
            setBusy(true, "Testing model")
            runCatching {
                validateSettings(state.providerSettings)
                val previousSettings = app.settingsStore.preferences.first().providerSettings
                val previousKey = app.secureApiKeyStore.read()
                val candidateKey = state.apiKeyDraft.ifBlank { previousKey ?: error("Enter an API key first") }
                app.providerClient.test(state.providerSettings, candidateKey)
                try {
                    if (state.apiKeyDraft.isNotBlank()) app.secureApiKeyStore.save(candidateKey)
                    app.settingsStore.saveProvider(state.providerSettings)
                } catch (failure: Throwable) {
                    if (previousKey == null) app.secureApiKeyStore.clear() else app.secureApiKeyStore.save(previousKey)
                    runCatching { app.settingsStore.saveProvider(previousSettings) }
                    throw failure
                }
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        apiKeyConfigured = true,
                        apiKeyDraft = "",
                        resultNotice = "Model connection saved",
                        error = null
                    )
                }
            }.onFailure { failure ->
                _uiState.update {
                    it.copy(error = failure.safeMessage(), lastErrorKind = failure.errorKind(), resultNotice = null)
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

    fun sendChat() {
        val state = _uiState.value
        val prompt = state.draftGoal.trim()
        if (prompt.isBlank()) {
            _uiState.update { it.copy(error = "Write what you want PocketAgent to do") }
            return
        }
        if (hasRunningTurn(state.currentTurn)) {
            _uiState.update { it.copy(error = "The current reply is still running. Wait or cancel it first.") }
            return
        }
        if (searchEngine.looksLikeDocumentSearch(prompt)) {
            startDocumentSearch(prompt)
            return
        }
        if (!state.apiKeyConfigured) {
            _uiState.update { it.copy(section = AppSection.SETTINGS, error = "Set up and test a model first") }
            return
        }
        sendPlainChat(prompt)
    }

    private fun sendPlainChat(prompt: String) {
        if (activeWorkJob?.isActive == true || _uiState.value.busy) return
        val state = _uiState.value
        val history = contextPolicy.requestMessages(state.snapshot?.messages.orEmpty())
        setBusy(true, "Replying")
        activeWorkJob = viewModelScope.launch {
            var turn: AgentTurn? = null
            try {
                turn = app.repository.beginChatTurn(state.selectedThreadId, prompt, null)
                _uiState.update { it.copy(selectedThreadId = turn?.threadId, draftGoal = "", error = null) }
                loadSelectedThread()
                val reply = app.providerClient.chatReply(
                    prompt = prompt,
                    history = history,
                    attachments = emptyList(),
                    settings = state.providerSettings,
                    apiKey = requireApiKey()
                )
                app.repository.completeChatTurn(requireNotNull(turn), reply)
                _uiState.update { it.copy(resultNotice = null, error = null) }
                loadSelectedThread()
            } catch (_: CancellationException) {
                turn?.let { app.repository.updateStatus(it, TurnStatus.CANCELLED, "Cancelled") }
                loadSelectedThread()
            } catch (failure: Throwable) {
                turn?.let { app.repository.failChatTurn(it, failure.safeMessage()) }
                _uiState.update { it.copy(error = failure.safeMessage(), lastErrorKind = failure.errorKind()) }
                loadSelectedThread()
            } finally {
                activeWorkJob = null
                setBusy(false)
            }
        }
    }

    fun startDocumentSearch(query: String = _uiState.value.draftGoal.trim()) {
        if (query.isBlank()) return
        viewModelScope.launch {
            val grants = app.repository.loadDocumentGrants()
            _uiState.update { it.copy(documentGrants = grants, error = null) }
            if (grants.isEmpty()) {
                _uiState.update {
                    it.copy(
                        documentSearchRequest = DocumentSearchRequest(
                            query = query,
                            stage = DocumentSearchStage.WAITING_FOR_SCOPE,
                            requestedAction = DocumentAccessAction.PICK_FOLDER,
                            message = "PocketAgent needs a file or folder range before it can search phone documents."
                        ),
                        resultNotice = null,
                        error = null
                    )
                }
            } else {
                runDocumentSearch(query)
            }
        }
    }

    fun searchAuthorizedDocuments() {
        val query = _uiState.value.documentSearchRequest?.query ?: _uiState.value.draftGoal.trim()
        if (query.isNotBlank()) runDocumentSearch(query)
    }

    fun dismissDocumentAccessRequest() {
        _uiState.update { it.copy(documentSearchRequest = null, error = null) }
    }

    fun onFilesGranted(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val grants = withContext(Dispatchers.IO) { uris.map { extractor.createFileGrant(it) } }
            grants.forEach { app.repository.saveDocumentGrant(it) }
            refreshDocumentGrants()
            val query = _uiState.value.documentSearchRequest?.query ?: _uiState.value.draftGoal.trim()
            if (query.isNotBlank()) runDocumentSearch(query) else {
                _uiState.update { it.copy(resultNotice = "Files authorized for document search") }
            }
        }
    }

    fun onFolderGranted(uri: Uri) {
        viewModelScope.launch {
            val grant = withContext(Dispatchers.IO) { extractor.createTreeGrant(uri) }
            app.repository.saveDocumentGrant(grant)
            refreshDocumentGrants()
            val query = _uiState.value.documentSearchRequest?.query ?: _uiState.value.draftGoal.trim()
            if (query.isNotBlank()) runDocumentSearch(query) else {
                _uiState.update { it.copy(resultNotice = "Folder authorized for document search") }
            }
        }
    }

    fun removeDocumentGrant(grantId: String) {
        viewModelScope.launch {
            runCatching {
                app.repository.removeDocumentGrant(grantId)?.let { grant ->
                    getApplication<Application>().contentResolver.releasePersistableUriPermission(
                        Uri.parse(grant.uri),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }.onSuccess {
                refreshDocumentGrants()
                _uiState.update { it.copy(resultNotice = "Search range removed", error = null) }
            }.onFailure { failure ->
                _uiState.update { it.copy(error = failure.safeMessage(), lastErrorKind = failure.errorKind()) }
            }
        }
    }

    private fun runDocumentSearch(query: String) {
        if (activeWorkJob?.isActive == true || _uiState.value.busy) return
        val startState = _uiState.value
        val history = contextPolicy.requestMessages(startState.snapshot?.messages.orEmpty())
        setBusy(true, "Searching documents")
        activeWorkJob = viewModelScope.launch {
            var turn: AgentTurn? = null
            try {
                turn = app.repository.beginChatTurn(startState.selectedThreadId, query, null)
                _uiState.update {
                    it.copy(
                        selectedThreadId = turn?.threadId,
                        draftGoal = "",
                        documentSearchRequest = DocumentSearchRequest(
                            query = query,
                            stage = DocumentSearchStage.INDEXING,
                            message = "Reading authorized documents"
                        ),
                        documentSearchResults = emptyList(),
                        error = null
                    )
                }
                loadSelectedThread()

                app.repository.recordDocumentSearch(query, DocumentSearchStage.INDEXING)
                val grants = app.repository.loadDocumentGrants()
                val entries = withContext(Dispatchers.IO) {
                    grants.flatMap { grant ->
                        val indexed = runCatching { extractor.indexGrant(grant) }.getOrElse { emptyList() }
                        indexed.also { app.repository.replaceDocumentIndex(grant.id, it) }
                    }
                }
                _uiState.update {
                    it.copy(
                        documentSearchRequest = DocumentSearchRequest(
                            query = query,
                            stage = DocumentSearchStage.SEARCHING_RECENT,
                            message = "Ranking recent authorized documents"
                        )
                    )
                }
                app.repository.recordDocumentSearch(query, DocumentSearchStage.SEARCHING_RECENT)
                val searchable = if (entries.isNotEmpty()) entries else app.repository.loadDocumentIndex()
                val results = searchEngine.search(query, searchable)
                if (results.isEmpty()) {
                    val message = "I searched the authorized range but did not find a strong match. Authorize a broader folder such as Download or Documents and try again."
                    _uiState.update {
                        it.copy(
                            documentSearchRequest = DocumentSearchRequest(
                                query = query,
                                stage = DocumentSearchStage.NEEDS_BROADER_SCOPE,
                                requestedAction = DocumentAccessAction.EXPAND_SCOPE,
                                message = message,
                                results = emptyList()
                            ),
                            documentSearchResults = emptyList()
                        )
                    }
                    app.repository.completeChatTurn(requireNotNull(turn), message)
                    loadSelectedThread()
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        documentSearchRequest = DocumentSearchRequest(
                            query = query,
                            stage = DocumentSearchStage.UNDERSTANDING,
                            message = "Understanding the best matches",
                            results = results
                        ),
                        documentSearchResults = results
                    )
                }
                app.repository.recordDocumentSearch(query, DocumentSearchStage.UNDERSTANDING)
                val answer = if (startState.apiKeyConfigured) {
                    app.providerClient.answerDocumentSearch(
                        query = query,
                        results = results,
                        history = history,
                        settings = startState.providerSettings,
                        apiKey = requireApiKey()
                    )
                } else {
                    localSearchAnswer(query, results) + "\n\nSet up a model in Settings for deeper understanding and reranking."
                }
                app.repository.completeChatTurn(requireNotNull(turn), answer)
                _uiState.update {
                    it.copy(
                        documentSearchRequest = DocumentSearchRequest(
                            query = query,
                            stage = DocumentSearchStage.COMPLETED,
                            message = "Found ${results.size} likely document matches",
                            results = results
                        ),
                        documentSearchResults = results,
                        resultNotice = null,
                        error = null
                    )
                }
                app.repository.recordDocumentSearch(query, DocumentSearchStage.COMPLETED)
                loadSelectedThread()
            } catch (_: CancellationException) {
                turn?.let { app.repository.updateStatus(it, TurnStatus.CANCELLED, "Cancelled") }
                loadSelectedThread()
            } catch (failure: Throwable) {
                turn?.let { app.repository.failChatTurn(it, failure.safeMessage()) }
                _uiState.update {
                    it.copy(
                        documentSearchRequest = DocumentSearchRequest(
                            query = query,
                            stage = DocumentSearchStage.FAILED,
                            message = failure.safeMessage()
                        ),
                        error = failure.safeMessage(),
                        lastErrorKind = failure.errorKind()
                    )
                }
                loadSelectedThread()
            } finally {
                activeWorkJob = null
                setBusy(false)
                refreshDocumentGrants()
            }
        }
    }

    fun cancelTurn() {
        activeWorkJob?.cancel()
        _uiState.update { it.copy(error = null, resultNotice = "Cancelled") }
    }

    fun requestDeleteThread(threadId: String) {
        if (threadId == _uiState.value.selectedThreadId && !canLeaveActiveTurn()) return
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
                    draftGoal = "",
                    resultNotice = "Thread deleted",
                    error = null
                )
            }
        }
    }

    private suspend fun refreshDocumentGrants() {
        val grants = app.repository.loadDocumentGrants()
        _uiState.update { it.copy(documentGrants = grants) }
    }

    private suspend fun loadSelectedThread() {
        val threadId = _uiState.value.selectedThreadId ?: return
        val snapshot = app.repository.loadThread(threadId) ?: return
        _uiState.update { it.copy(snapshot = snapshot) }
    }

    private fun localSearchAnswer(query: String, results: List<DocumentSearchResult>): String = buildString {
        appendLine("I searched authorized phone documents for: $query")
        appendLine()
        results.take(5).forEachIndexed { index, result ->
            appendLine("${index + 1}. ${result.entry.name}")
            appendLine("   Match: ${result.reason}")
            appendLine("   Snippet: ${result.snippet.take(260)}")
        }
    }.trim()

    private fun canLeaveActiveTurn(): Boolean {
        if (activeWorkJob?.isActive == true || hasRunningTurn(_uiState.value.currentTurn)) {
            _uiState.update { it.copy(error = "A reply is still running. Cancel it before switching threads.") }
            return false
        }
        return true
    }

    private fun hasRunningTurn(turn: AgentTurn?): Boolean =
        turn?.status in setOf(TurnStatus.PLANNING, TurnStatus.RUNNING, TurnStatus.VERIFYING)

    private fun requireApiKey(): String = app.secureApiKeyStore.read() ?: error("API key is not configured")

    private fun validateSettings(settings: ProviderSettings) {
        require(settings.providerId in ProviderPresets.all.map { it.id }) { "Choose a supported model provider" }
        val endpoint = URI(settings.endpoint)
        val local = endpoint.host in setOf("127.0.0.1", "localhost", "::1")
        require(endpoint.scheme == "https" || (endpoint.scheme == "http" && local)) {
            "Model endpoint must use HTTPS; HTTP is allowed only for local loopback."
        }
        require(settings.model.isNotBlank()) { "Model name is required" }
    }

    private fun setBusy(busy: Boolean, runningStep: String? = null) {
        _uiState.update {
            it.copy(
                busy = busy,
                runningStep = if (busy) runningStep else null,
                error = if (busy) null else it.error
            )
        }
    }

    private fun Throwable.safeMessage(): String {
        val value = message.orEmpty()
            .replace(Regex("""sk-[A-Za-z0-9_-]{8,}"""), "***REDACTED***")
            .take(500)
        return value.ifBlank { "Operation failed. Try again." }
    }

    private fun Throwable.errorKind(): AgentErrorKind = when (this) {
        is ProviderException -> kind
        is CancellationException -> AgentErrorKind.CANCELLED_BY_USER
        is java.net.SocketTimeoutException -> AgentErrorKind.NETWORK_TIMEOUT
        else -> AgentErrorKind.LOCAL_FAILURE
    }

    companion object {
        private const val MAX_GOAL_CHARS = 4_000

        fun factory(app: PocketAgentApplication): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = PocketAgentViewModel(app) as T
            }
    }
}