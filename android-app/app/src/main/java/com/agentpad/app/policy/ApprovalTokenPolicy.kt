package com.agentpad.app.policy

import com.agentpad.app.domain.ApprovalScope
import com.agentpad.app.domain.ApprovalToken
import com.agentpad.app.domain.PlannedAction
import com.agentpad.app.domain.TaskPlan

class ApprovalTokenPolicy(private val approvalPolicy: ApprovalPolicy) {
    fun createTaskToken(plan: TaskPlan, now: Long, ttlMillis: Long): ApprovalToken =
        ApprovalToken(
            taskId = plan.id,
            actionId = null,
            argumentDigest = taskDigest(plan),
            scope = ApprovalScope.TASK,
            expiresAt = now + ttlMillis,
            remainingUses = 1
        )

    fun createActionToken(
        plan: TaskPlan,
        action: PlannedAction,
        now: Long,
        ttlMillis: Long
    ): ApprovalToken =
        ApprovalToken(
            taskId = plan.id,
            actionId = action.id,
            argumentDigest = approvalPolicy.argumentDigest(action),
            scope = ApprovalScope.ACTION,
            expiresAt = now + ttlMillis,
            remainingUses = 1
        )

    fun isTaskValid(token: ApprovalToken?, plan: TaskPlan, now: Long): Boolean =
        token != null &&
            token.taskId == plan.id &&
            token.actionId == null &&
            token.scope == ApprovalScope.TASK &&
            token.argumentDigest == taskDigest(plan) &&
            token.expiresAt >= now &&
            token.remainingUses > 0

    fun isActionValid(
        token: ApprovalToken?,
        plan: TaskPlan,
        action: PlannedAction,
        now: Long
    ): Boolean =
        token != null &&
            token.taskId == plan.id &&
            token.actionId == action.id &&
            token.scope == ApprovalScope.ACTION &&
            token.argumentDigest == approvalPolicy.argumentDigest(action) &&
            token.expiresAt >= now &&
            token.remainingUses > 0

    fun consume(token: ApprovalToken): ApprovalToken {
        require(token.remainingUses > 0) { "审批令牌已使用" }
        return token.copy(remainingUses = token.remainingUses - 1)
    }

    fun taskDigest(plan: TaskPlan): String =
        plan.actions
            .filter { approvalPolicy.requiredScope(it) == ApprovalScope.TASK }
            .joinToString("|") { approvalPolicy.argumentDigest(it) }

    fun taskTokenKey(taskId: String): String = "task:$taskId"
}
