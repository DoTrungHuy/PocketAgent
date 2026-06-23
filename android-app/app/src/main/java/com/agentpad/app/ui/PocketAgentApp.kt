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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agentpad.app.BuildConfig
import com.agentpad.app.data.ThemePreference
import com.agentpad.app.domain.AgentErrorKind
import com.agentpad.app.domain.AgentThread
import com.agentpad.app.domain.DocumentGrant
import com.agentpad.app.domain.DocumentGrantKind
import com.agentpad.app.domain.DocumentSearchRequest
import com.agentpad.app.domain.DocumentSearchResult
import com.agentpad.app.domain.DocumentSearchStage
import com.agentpad.app.domain.MessageKind
import com.agentpad.app.domain.MessageRole
import com.agentpad.app.domain.ProviderPresets
import com.agentpad.app.domain.ThreadMessage
import com.agentpad.app.ui.theme.PocketAgentTheme
import com.agentpad.app.ui.theme.SearchAmber
import com.agentpad.app.ui.theme.Success
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

@Composable
fun PocketAgentRoot(
    viewModel: PocketAgentViewModel,
    onChooseFiles: () -> Unit,
    onChooseFolder: () -> Unit,
    onPrivacyModeChanged: (Boolean) -> Unit,
    onExportDiagnostics: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val threads by viewModel.threads.collectAsStateWithLifecycle()

    LaunchedEffect(state.privacyMode) {
        onPrivacyModeChanged(state.privacyMode)
    }

    PocketAgentTheme(darkTheme = state.theme == ThemePreference.DARK) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            LaunchedEffect(state.section, maxWidth) {
                viewModel.recordUiContext(state.section, maxWidth.value.toInt())
            }
            if (maxWidth < 720.dp) {
                PhoneLayout(state, threads, viewModel, onChooseFiles, onChooseFolder, onExportDiagnostics)
            } else {
                WideLayout(state, threads, viewModel, onChooseFiles, onChooseFolder, onExportDiagnostics)
            }
        }
        DocumentAccessSheet(state, viewModel, onChooseFiles, onChooseFolder)
        Dialogs(state, viewModel, onExportDiagnostics)
    }
}

@Composable
private fun PhoneLayout(
    state: PocketAgentUiState,
    threads: List<AgentThread>,
    viewModel: PocketAgentViewModel,
    onChooseFiles: () -> Unit,
    onChooseFolder: () -> Unit,
    onExportDiagnostics: () -> Unit
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
                HistorySidebar(
                    threads = threads,
                    selectedId = state.selectedThreadId,
                    state = state,
                    viewModel = viewModel,
                    onClose = { scope.launch { drawerState.close() } },
                    modifier = Modifier.width(304.dp)
                )
            }
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                AppHeader(
                    state = state,
                    onMenu = { scope.launch { drawerState.open() } },
                    onNew = viewModel::newThread,
                    showMenu = true
                )
            }
        ) { padding ->
            MainContent(
                state = state,
                viewModel = viewModel,
                onChooseFiles = onChooseFiles,
                onChooseFolder = onChooseFolder,
                onExportDiagnostics = onExportDiagnostics,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun WideLayout(
    state: PocketAgentUiState,
    threads: List<AgentThread>,
    viewModel: PocketAgentViewModel,
    onChooseFiles: () -> Unit,
    onChooseFolder: () -> Unit,
    onExportDiagnostics: () -> Unit
) {
    Row(Modifier.fillMaxSize()) {
        HistorySidebar(
            threads = threads,
            selectedId = state.selectedThreadId,
            state = state,
            viewModel = viewModel,
            onClose = {},
            modifier = Modifier.width(304.dp)
        )
        VerticalDivider(color = MaterialTheme.colorScheme.outline)
        Column(Modifier.weight(1f).fillMaxHeight()) {
            AppHeader(state = state, onMenu = {}, onNew = viewModel::newThread, showMenu = false)
            MainContent(
                state = state,
                viewModel = viewModel,
                onChooseFiles = onChooseFiles,
                onChooseFolder = onChooseFolder,
                onExportDiagnostics = onExportDiagnostics,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MainContent(
    state: PocketAgentUiState,
    viewModel: PocketAgentViewModel,
    onChooseFiles: () -> Unit,
    onChooseFolder: () -> Unit,
    onExportDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (state.section) {
        AppSection.THREAD -> ChatPage(state, viewModel, onChooseFiles, onChooseFolder, modifier)
        AppSection.SETTINGS -> SettingsPage(state, viewModel, onExportDiagnostics, modifier)
    }
}

@Composable
private fun AppHeader(
    state: PocketAgentUiState,
    onMenu: () -> Unit,
    onNew: () -> Unit,
    showMenu: Boolean
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showMenu) {
                IconButton(onClick = onMenu) { Icon(Icons.Rounded.Menu, contentDescription = "History") }
            }
            PocketMark()
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("PocketAgent", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    state.snapshot?.thread?.title ?: "New document search",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            ModelBadge(state)
            IconButton(onClick = onNew) { Icon(Icons.Rounded.Add, contentDescription = "New chat") }
        }
    }
}

@Composable
private fun PocketMark() {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text("P", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun ModelBadge(state: PocketAgentUiState) {
    val provider = ProviderPresets.byId(state.providerSettings.providerId)?.name ?: state.providerSettings.providerId
    AssistChip(
        onClick = {},
        enabled = false,
        label = {
            Text(
                if (state.apiKeyConfigured) provider else "Set model",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}

@Composable
private fun HistorySidebar(
    threads: List<AgentThread>,
    selectedId: String?,
    state: PocketAgentUiState,
    viewModel: PocketAgentViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PocketMark()
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("PocketAgent", fontWeight = FontWeight.Bold)
                    Text("phone document agent", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Close") }
            }
            Spacer(Modifier.height(14.dp))
            Button(onClick = { viewModel.newThread(); onClose() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("New chat")
            }
            Spacer(Modifier.height(10.dp))
            SidebarButton("Chats", Icons.Rounded.History, state.section == AppSection.THREAD) {
                viewModel.setSection(AppSection.THREAD)
                onClose()
            }
            SidebarButton("Settings", Icons.Rounded.Settings, state.section == AppSection.SETTINGS) {
                viewModel.setSection(AppSection.SETTINGS)
                onClose()
            }
            Spacer(Modifier.height(18.dp))
            Text("History", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(threads, key = { it.id }) { thread ->
                    ThreadRow(
                        thread = thread,
                        selected = thread.id == selectedId,
                        onOpen = { viewModel.openThread(thread.id); onClose() },
                        onDelete = { viewModel.requestDeleteThread(thread.id) }
                    )
                }
            }
            HorizontalDivider()
            Spacer(Modifier.height(10.dp))
            Text(
                "Search ranges: ${state.documentGrants.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SidebarButton(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

@Composable
private fun ThreadRow(thread: AgentThread, selected: Boolean, onOpen: () -> Unit, onDelete: () -> Unit) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(thread.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(thread.updatedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ChatPage(
    state: PocketAgentUiState,
    viewModel: PocketAgentViewModel,
    onChooseFiles: () -> Unit,
    onChooseFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val messages = state.snapshot?.messages.orEmpty()
            if (messages.isEmpty() && state.documentSearchRequest == null) {
                item { EmptyConversation() }
            }
            state.documentSearchRequest?.let { request ->
                item { DocumentSearchStatusCard(request, state.documentSearchResults) }
            }
            if (state.documentGrants.isNotEmpty()) {
                item { GrantStrip(state.documentGrants, viewModel) }
            }
            items(messages, key = { it.id }) { message -> MessageBubble(message) }
            state.resultNotice?.let { item { NoticeCard(it, Success) } }
            state.error?.let { item { NoticeCard("${errorPrefix(state.lastErrorKind)}$it", MaterialTheme.colorScheme.error) } }
        }
        PromptBar(state, viewModel, onChooseFiles, onChooseFolder)
    }
}

@Composable
private fun EmptyConversation() {
    Surface(color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(14.dp).size(30.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text("Ask for a document by content", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "PocketAgent will ask for a search range when it needs phone files.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MessageBubble(message: ThreadMessage) {
    val isUser = message.role == MessageRole.USER
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            contentColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(8.dp),
            border = if (isUser) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth(if (isUser) 0.86f else 0.94f)
        ) {
            Column(Modifier.padding(13.dp)) {
                Text(messageLabel(message), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text(message.content)
                Text(
                    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun DocumentSearchStatusCard(request: DocumentSearchRequest, results: List<DocumentSearchResult>) {
    Panel(borderColor = SearchAmber) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = SearchAmber.copy(alpha = 0.16f)) {
                Icon(Icons.Rounded.Search, null, tint = SearchAmber, modifier = Modifier.padding(8.dp).size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(searchStageLabel(request.stage), fontWeight = FontWeight.Bold)
                Text(request.message.ifBlank { request.query }, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val shown = if (results.isNotEmpty()) results else request.results
        if (shown.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            shown.take(3).forEach { result ->
                ResultRow(result)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ResultRow(result: DocumentSearchResult) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Description, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(result.entry.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(result.reason, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(result.snippet, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun GrantStrip(grants: List<DocumentGrant>, viewModel: PocketAgentViewModel) {
    Panel {
        Text("Authorized search ranges", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        grants.take(4).forEach { grant ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (grant.kind == DocumentGrantKind.TREE) Icons.Rounded.Folder else Icons.Rounded.Description, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(grant.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = { viewModel.removeDocumentGrant(grant.id) }) { Text("Remove") }
            }
        }
    }
}

@Composable
private fun PromptBar(
    state: PocketAgentUiState,
    viewModel: PocketAgentViewModel,
    onChooseFiles: () -> Unit,
    onChooseFolder: () -> Unit
) {
    Surface(tonalElevation = 2.dp, color = MaterialTheme.colorScheme.surface, modifier = Modifier.navigationBarsPadding()) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            OutlinedTextField(
                value = state.draftGoal,
                onValueChange = viewModel::setDraftGoal,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Ask PocketAgent to find document content...") },
                minLines = 1,
                maxLines = 4,
                enabled = !state.busy
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { viewModel.startDocumentSearch() }, enabled = !state.busy && state.draftGoal.isNotBlank()) {
                    Icon(Icons.Rounded.Search, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Find")
                }
                OutlinedButton(onClick = onChooseFolder, enabled = !state.busy) {
                    Icon(Icons.Rounded.Folder, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Scope")
                }
                Spacer(Modifier.weight(1f))
                if (state.busy) {
                    OutlinedButton(onClick = viewModel::cancelTurn) {
                        Icon(Icons.Rounded.StopCircle, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Cancel")
                    }
                }
                Button(onClick = viewModel::sendChat, enabled = state.draftGoal.isNotBlank() && !state.busy) {
                    Icon(Icons.Rounded.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentAccessSheet(
    state: PocketAgentUiState,
    viewModel: PocketAgentViewModel,
    onChooseFiles: () -> Unit,
    onChooseFolder: () -> Unit
) {
    val request = state.documentSearchRequest ?: return
    val shouldShow = request.stage == DocumentSearchStage.WAITING_FOR_SCOPE ||
        request.stage == DocumentSearchStage.NEEDS_BROADER_SCOPE
    if (!shouldShow) return
    ModalBottomSheet(onDismissRequest = viewModel::dismissDocumentAccessRequest) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Authorize document search", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                request.message.ifBlank { "PocketAgent needs an authorized phone file range before it can search by content." },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.documentGrants.isNotEmpty()) {
                Button(onClick = viewModel::searchAuthorizedDocuments, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Search, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Search authorized recent documents")
                }
            }
            OutlinedButton(onClick = onChooseFolder, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Folder, null)
                Spacer(Modifier.width(8.dp))
                Text("Authorize a folder")
            }
            OutlinedButton(onClick = onChooseFiles, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Description, null)
                Spacer(Modifier.width(8.dp))
                Text("Choose files")
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun SettingsPage(
    state: PocketAgentUiState,
    viewModel: PocketAgentViewModel,
    onExportDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings = state.providerSettings
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { PageHeading("Settings", "Model, privacy, and diagnostics") }
        item {
            Panel {
                Text("Provider", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                ProviderPresets.all.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { preset ->
                            val selected = settings.providerId == preset.id
                            if (selected) {
                                Button(onClick = { viewModel.selectProvider(preset.id) }, modifier = Modifier.weight(1f)) {
                                    Text(preset.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            } else {
                                OutlinedButton(onClick = { viewModel.selectProvider(preset.id) }, modifier = Modifier.weight(1f)) {
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
                OutlinedTextField(settings.endpoint, { viewModel.setProviderSettings(settings.copy(endpoint = it)) }, Modifier.fillMaxWidth(), label = { Text("Endpoint") }, singleLine = true)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(settings.model, { viewModel.setProviderSettings(settings.copy(model = it)) }, Modifier.fillMaxWidth(), label = { Text("Model") }, singleLine = true)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(settings.visionEndpoint, { viewModel.setProviderSettings(settings.copy(visionEndpoint = it)) }, Modifier.fillMaxWidth(), label = { Text("Vision endpoint") }, singleLine = true)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(settings.visionModel, { viewModel.setProviderSettings(settings.copy(visionModel = it)) }, Modifier.fillMaxWidth(), label = { Text("Vision model") }, singleLine = true)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = state.apiKeyDraft,
                    onValueChange = viewModel::setApiKeyDraft,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (state.apiKeyConfigured) "API key (leave blank to keep)" else "API key") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                Button(onClick = viewModel::testAndSaveProvider, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Test and save")
                }
            }
        }
        item {
            Panel {
                Text("App", fontWeight = FontWeight.Bold)
                SettingSwitchRow("Dark theme", state.theme == ThemePreference.DARK) {
                    viewModel.setTheme(if (it) ThemePreference.DARK else ThemePreference.LIGHT)
                }
                SettingSwitchRow("Privacy mode", state.privacyMode, "Blocks screenshots and screen recording.", viewModel::setPrivacyMode)
            }
        }
        item {
            Panel {
                Text("Version", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                Text("${BuildConfig.GIT_SHA} / ${BuildConfig.BUILD_CHANNEL}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onExportDiagnostics, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Download, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Export diagnostics")
                }
            }
        }
        state.resultNotice?.let { item { NoticeCard(it, Success) } }
        state.error?.let { item { NoticeCard("${errorPrefix(state.lastErrorKind)}$it", MaterialTheme.colorScheme.error) } }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SettingSwitchRow(title: String, checked: Boolean, description: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Panel(borderColor: Color = MaterialTheme.colorScheme.outline, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, borderColor.copy(alpha = 0.72f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

@Composable
private fun PageHeading(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NoticeCard(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, color.copy(alpha = 0.4f))) {
        Text(text, color = color, modifier = Modifier.padding(12.dp))
    }
}

@Composable
private fun Dialogs(state: PocketAgentUiState, viewModel: PocketAgentViewModel, onExportDiagnostics: () -> Unit) {
    if (state.deleteConfirmationThreadId != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteThread,
            title = { Text("Delete chat?") },
            text = { Text("This removes the local messages in this chat. Search range permissions stay available unless removed separately.") },
            confirmButton = { TextButton(onClick = viewModel::confirmDeleteThread) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = viewModel::dismissDeleteThread) { Text("Cancel") } }
        )
    }
    if (state.crashReportAvailable && state.deleteConfirmationThreadId == null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissCrashReport,
            title = { Text("Previous crash detected") },
            text = { Text("PocketAgent saved a local diagnostic report without API keys or full document text.") },
            confirmButton = { TextButton(onClick = onExportDiagnostics) { Text("Export") } },
            dismissButton = { TextButton(onClick = viewModel::dismissCrashReport) { Text("Ignore") } }
        )
    }
}

private fun messageLabel(message: ThreadMessage): String = when {
    message.role == MessageRole.USER -> "You"
    message.kind == MessageKind.CONTEXT_SUMMARY -> "Checkpoint"
    else -> "PocketAgent"
}

private fun searchStageLabel(stage: DocumentSearchStage): String = when (stage) {
    DocumentSearchStage.WAITING_FOR_SCOPE -> "Waiting for search range"
    DocumentSearchStage.INDEXING -> "Indexing documents"
    DocumentSearchStage.SEARCHING_RECENT -> "Searching recent documents"
    DocumentSearchStage.UNDERSTANDING -> "Understanding matches"
    DocumentSearchStage.NEEDS_BROADER_SCOPE -> "Expand search range"
    DocumentSearchStage.COMPLETED -> "Search complete"
    DocumentSearchStage.FAILED -> "Search failed"
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
