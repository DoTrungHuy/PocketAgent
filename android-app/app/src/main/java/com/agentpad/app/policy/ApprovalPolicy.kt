package com.agentpad.app.policy

import com.agentpad.app.domain.ApprovalScope
import com.agentpad.app.domain.PlannedAction
import com.agentpad.app.domain.RiskLevel
import java.security.MessageDigest

class ApprovalPolicy {
    private val riskByTool = mapOf(
        "inspect_task" to RiskLevel.READ_ONLY,
        "read_document_metadata" to RiskLevel.READ_ONLY,
        "read_document" to RiskLevel.READ_ONLY,
        "open_url" to RiskLevel.TASK_APPROVAL,
        "launch_app" to RiskLevel.TASK_APPROVAL,
        "share_preview" to RiskLevel.TASK_APPROVAL,
        "upload_document_for_summary" to RiskLevel.ACTION_APPROVAL,
        "write_document" to RiskLevel.ACTION_APPROVAL,
        "delete_document" to RiskLevel.ACTION_APPROVAL,
        "send_text" to RiskLevel.ACTION_APPROVAL,
        "capture_screen" to RiskLevel.ACTION_APPROVAL,
        "accessibility_input" to RiskLevel.ACTION_APPROVAL,
        "install_package" to RiskLevel.ACTION_APPROVAL,
        "run_command" to RiskLevel.ACTION_APPROVAL,
        "payment" to RiskLevel.FORBIDDEN,
        "read_password" to RiskLevel.FORBIDDEN,
        "read_otp" to RiskLevel.FORBIDDEN,
        "bypass_lock_screen" to RiskLevel.FORBIDDEN,
        "silent_install" to RiskLevel.FORBIDDEN
    )

    fun knownTools(): Set<String> = riskByTool.keys

    fun riskFor(tool: String): RiskLevel = riskByTool[tool] ?: RiskLevel.FORBIDDEN

    fun normalize(action: PlannedAction): PlannedAction {
        val localRisk = riskFor(action.tool)
        val effectiveRisk = maxOf(localRisk, action.risk)
        return action.copy(risk = effectiveRisk)
    }

    fun requiredScope(action: PlannedAction): ApprovalScope = when (normalize(action).risk) {
        RiskLevel.READ_ONLY -> ApprovalScope.NONE
        RiskLevel.TASK_APPROVAL -> ApprovalScope.TASK
        RiskLevel.ACTION_APPROVAL -> ApprovalScope.ACTION
        RiskLevel.FORBIDDEN -> ApprovalScope.ACTION
    }

    fun argumentDigest(action: PlannedAction): String {
        val canonical = buildString {
            append(action.tool)
            action.arguments.toSortedMap().forEach { (key, value) ->
                append('\u0000')
                append(key)
                append('=')
                append(value)
            }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
