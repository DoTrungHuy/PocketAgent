package com.agentpad.app.domain

import java.util.UUID

enum class TaskStatus {
    DRAFT,
    PLANNING,
    AWAITING_APPROVAL,
    RUNNING,
    VERIFYING,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class RiskLevel {
    READ_ONLY,
    TASK_APPROVAL,
    ACTION_APPROVAL,
    FORBIDDEN
}

enum class ApprovalScope {
    NONE,
    TASK,
    ACTION
}

enum class CapabilityState {
    AVAILABLE,
    NEEDS_CONFIGURATION,
    NEEDS_PERMISSION,
    PLANNED,
    UNAVAILABLE
}

data class PlannedAction(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val tool: String,
    val arguments: Map<String, String> = emptyMap(),
    val risk: RiskLevel,
    val reversible: Boolean
)

data class TaskPlan(
    val id: String = UUID.randomUUID().toString(),
    val goal: String,
    val title: String,
    val summary: String,
    val actions: List<PlannedAction>,
    val stopCondition: String = "目标完成、用户取消、达到限制或出现无法安全处理的错误",
    val maxSteps: Int = 8,
    val createdAt: Long = System.currentTimeMillis()
) {
    val highestRisk: RiskLevel
        get() = actions.maxOfOrNull { it.risk } ?: RiskLevel.READ_ONLY
}

data class ApprovalToken(
    val id: String = UUID.randomUUID().toString(),
    val taskId: String,
    val actionId: String?,
    val argumentDigest: String,
    val scope: ApprovalScope,
    val expiresAt: Long,
    val remainingUses: Int
)

data class ToolResult(
    val actionId: String,
    val success: Boolean,
    val summary: String,
    val evidence: String? = null,
    val errorCode: String? = null,
    val recoverable: Boolean = false
)

data class CapabilityDescriptor(
    val id: String,
    val name: String,
    val description: String,
    val state: CapabilityState,
    val risk: RiskLevel,
    val enableHint: String
)

data class ProviderSettings(
    val providerId: String = "deepseek",
    val endpoint: String = "https://api.deepseek.com/chat/completions",
    val model: String = "",
    val visionEndpoint: String = "",
    val visionModel: String = ""
)

data class TaskRecord(
    val id: String,
    val title: String,
    val goal: String,
    val status: TaskStatus,
    val updatedAt: Long,
    val result: String?
)
