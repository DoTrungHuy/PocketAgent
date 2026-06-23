package com.agentpad.app.tool

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.agentpad.app.domain.PlannedAction
import com.agentpad.app.domain.ToolResult

class AndroidToolExecutor(private val context: Context) {
    val availableTools: Set<String> = setOf(
        "inspect_task",
        "read_document_metadata",
        "read_document",
        "upload_document_for_summary",
        "open_url",
        "launch_app",
        "share_preview"
    )

    fun executeIntentAction(action: PlannedAction): ToolResult = when (action.tool) {
        "open_url" -> openUrl(action)
        "launch_app" -> launchApp(action)
        "share_preview" -> sharePreview(action)
        "inspect_task" -> ToolResult(actionId = action.id, success = true, summary = "Task inspected")
        else -> ToolResult(
            actionId = action.id,
            success = false,
            summary = "This Android tool is not available in the current version",
            errorCode = "TOOL_NOT_AVAILABLE",
            recoverable = false
        )
    }

    private fun openUrl(action: PlannedAction): ToolResult {
        val raw = action.arguments["url"].orEmpty().trim()
        val uri = runCatching { Uri.parse(raw) }.getOrNull()
        if (uri == null || uri.scheme != "https" || uri.host.isNullOrBlank()) {
            return ToolResult(action.id, false, "URL must be a valid HTTPS address", errorCode = "INVALID_URL")
        }
        return runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ToolResult(action.id, true, "Opened in the system browser", evidence = uri.host)
        }.getOrElse {
            ToolResult(action.id, false, "No app can open this URL", errorCode = "NO_HANDLER")
        }
    }

    private fun launchApp(action: PlannedAction): ToolResult {
        val packageName = action.arguments["package"].orEmpty().trim()
        if (packageName.isBlank()) {
            return ToolResult(action.id, false, "Package name is missing", errorCode = "MISSING_PACKAGE")
        }
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return ToolResult(action.id, false, "App was not found", errorCode = "APP_NOT_FOUND")
        return runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ToolResult(action.id, true, "App launched", evidence = packageName)
        }.getOrElse {
            ToolResult(action.id, false, "App launch failed", errorCode = "LAUNCH_FAILED")
        }
    }

    private fun sharePreview(action: PlannedAction): ToolResult {
        val text = action.arguments["text"].orEmpty().take(10_000)
        if (text.isBlank()) {
            return ToolResult(action.id, false, "Nothing to share", errorCode = "EMPTY_SHARE")
        }
        return runCatching {
            val share = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, text)
            context.startActivity(Intent.createChooser(share, "Choose share target").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ToolResult(action.id, true, "Opened the system share sheet")
        }.getOrElse {
            ToolResult(action.id, false, "Unable to open share sheet", errorCode = "SHARE_FAILED")
        }
    }
}