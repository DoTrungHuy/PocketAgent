package com.agentpad.app.domain

import java.util.UUID

enum class TurnStatus {
    DRAFT,
    PLANNING,
    AWAITING_APPROVAL,
    RUNNING,
    VERIFYING,
    COMPLETED,
    FAILED,
    CANCELLED,
    INTERRUPTED,
    SUPERSEDED
}

typealias TaskStatus = TurnStatus

enum class ThreadStatus {
    ACTIVE,
    COMPLETED,
    FAILED
}

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

enum class MessageKind {
    GOAL,
    PLAN,
    RESULT,
    STATUS,
    CONTEXT_SUMMARY
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

enum class AgentErrorKind {
    NONE,
    RATE_LIMITED,
    NETWORK_TIMEOUT,
    PROVIDER_RETRYABLE,
    PROVIDER_REJECTED,
    INVALID_RESPONSE,
    CANCELLED_BY_USER,
    LOCAL_FAILURE
}

data class ProviderPreset(
    val id: String,
    val name: String,
    val endpoint: String,
    val defaultModel: String,
    val mainland: Boolean = true,
    val supportsStreaming: Boolean = true
)

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
    val model: String = "deepseek-v4-flash",
    val streamingEnabled: Boolean = false,
    val visionEndpoint: String = "",
    val visionModel: String = ""
)

object ProviderPresets {
    val all = listOf(
        ProviderPreset(
            id = "deepseek",
            name = "DeepSeek",
            endpoint = "https://api.deepseek.com/chat/completions",
            defaultModel = "deepseek-v4-flash"
        ),
        ProviderPreset(
            id = "dashscope",
            name = "阿里云百炼 / 通义",
            endpoint = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
            defaultModel = "qwen-plus"
        ),
        ProviderPreset(
            id = "moonshot",
            name = "Kimi / Moonshot",
            endpoint = "https://api.moonshot.cn/v1/chat/completions",
            defaultModel = "kimi-k2.5"
        ),
        ProviderPreset(
            id = "zhipu",
            name = "智谱 GLM",
            endpoint = "https://open.bigmodel.cn/api/paas/v4/chat/completions",
            defaultModel = "glm-5.1"
        ),
        ProviderPreset(
            id = "minimax",
            name = "MiniMax 中国大陆",
            endpoint = "https://api.minimaxi.com/v1/chat/completions",
            defaultModel = "MiniMax-M2.7"
        ),
        ProviderPreset(
            id = "siliconflow",
            name = "硅基流动 SiliconFlow",
            endpoint = "https://api.siliconflow.cn/v1/chat/completions",
            defaultModel = ""
        ),
        ProviderPreset(
            id = "volcengine",
            name = "火山方舟 / 豆包",
            endpoint = "https://ark.cn-beijing.volces.com/api/v3/chat/completions",
            defaultModel = "doubao-seed-2-0-lite-260215"
        ),
        ProviderPreset(
            id = "qianfan",
            name = "百度智能云千帆",
            endpoint = "https://qianfan.baidubce.com/v2/chat/completions",
            defaultModel = ""
        ),
        ProviderPreset(
            id = "custom",
            name = "自定义 OpenAI-compatible",
            endpoint = "",
            defaultModel = "",
            mainland = false,
            supportsStreaming = false
        )
    )

    fun byId(id: String): ProviderPreset? = all.firstOrNull { it.id == id }
}

data class AgentThread(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val status: ThreadStatus = ThreadStatus.ACTIVE,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt
)

data class AgentTurn(
    val id: String = UUID.randomUUID().toString(),
    val threadId: String,
    val ordinal: Int,
    val goal: String,
    val plan: TaskPlan?,
    val status: TurnStatus,
    val result: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt
)

data class ThreadMessage(
    val id: String = UUID.randomUUID().toString(),
    val threadId: String,
    val turnId: String?,
    val role: MessageRole,
    val kind: MessageKind,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class ThreadAttachment(
    val id: String = UUID.randomUUID().toString(),
    val threadId: String,
    val turnId: String?,
    val uri: String,
    val name: String,
    val mimeType: String,
    val size: Long?,
    val createdAt: Long = System.currentTimeMillis()
)

data class ThreadSnapshot(
    val thread: AgentThread,
    val turns: List<AgentTurn>,
    val messages: List<ThreadMessage>,
    val attachments: List<ThreadAttachment>
)

data class TaskRecord(
    val id: String,
    val title: String,
    val goal: String,
    val status: TaskStatus,
    val updatedAt: Long,
    val result: String?
)
