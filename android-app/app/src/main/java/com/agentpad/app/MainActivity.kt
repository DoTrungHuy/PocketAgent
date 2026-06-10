package com.agentpad.app

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.agentpad.app.ui.AgentPadRoot
import com.agentpad.app.ui.AgentPadViewModel

class MainActivity : ComponentActivity() {
    private val app: AgentPadApplication
        get() = application as AgentPadApplication

    private val viewModel: AgentPadViewModel by viewModels {
        AgentPadViewModel.factory(app)
    }

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.selectDocument(uri)
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
            AgentPadRoot(
                viewModel = viewModel,
                onChooseDocument = {
                    openDocument.launch(
                        arrayOf(
                            "text/*",
                            "application/json",
                            "application/xml",
                            "text/markdown"
                        )
                    )
                },
                onPrivacyModeChanged = ::applyPrivacyMode,
                onExportDiagnostics = {
                    createDiagnosticFile.launch(
                        "AgentPad-diagnostics-${BuildConfig.VERSION_NAME}.json"
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
