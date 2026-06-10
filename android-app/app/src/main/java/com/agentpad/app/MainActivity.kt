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
import com.agentpad.app.ui.theme.AgentPadTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AgentPadViewModel by viewModels {
        AgentPadViewModel.factory(application as AgentPadApplication)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent {
            AgentPadTheme {
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
                    }
                )
            }
        }
    }
}
