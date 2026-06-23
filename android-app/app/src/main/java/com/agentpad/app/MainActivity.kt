package com.agentpad.app

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.agentpad.app.ui.PocketAgentRoot
import com.agentpad.app.ui.PocketAgentViewModel

class MainActivity : ComponentActivity() {
    private val app: PocketAgentApplication
        get() = application as PocketAgentApplication

    private val viewModel: PocketAgentViewModel by viewModels {
        PocketAgentViewModel.factory(app)
    }

    private val openDocuments = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        viewModel.onFilesGranted(uris)
    }

    private val openDocumentTree = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            viewModel.onFolderGranted(uri)
        }
    }

    private val createDiagnosticFile = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching { app.crashReporter.export(uri) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PocketAgentRoot(
                viewModel = viewModel,
                onChooseFiles = {
                    openDocuments.launch(
                        arrayOf(
                            "text/*",
                            "application/json",
                            "application/xml",
                            "text/html",
                            "text/markdown",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "application/pdf",
                            "application/octet-stream"
                        )
                    )
                },
                onChooseFolder = {
                    openDocumentTree.launch(null)
                },
                onPrivacyModeChanged = ::applyPrivacyMode,
                onExportDiagnostics = {
                    createDiagnosticFile.launch(
                        "PocketAgent-diagnostics-${BuildConfig.VERSION_NAME}.json"
                    )
                }
            )
        }
    }

    private fun applyPrivacyMode(enabled: Boolean) {
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
