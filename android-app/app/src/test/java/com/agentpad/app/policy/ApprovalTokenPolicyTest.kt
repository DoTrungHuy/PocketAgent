package com.agentpad.app.policy

import com.agentpad.app.domain.PlannedAction
import com.agentpad.app.domain.RiskLevel
import com.agentpad.app.domain.TaskPlan
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApprovalTokenPolicyTest {
    private val approvalPolicy = ApprovalPolicy()
    private val tokenPolicy = ApprovalTokenPolicy(approvalPolicy)
    private val action = PlannedAction(
        id = "action-1",
        title = "打开网页",
        description = "",
        tool = "open_url",
        arguments = mapOf("url" to "https://example.com"),
        risk = RiskLevel.TASK_APPROVAL,
        reversible = true
    )
    private val plan = TaskPlan(
        id = "task-1",
        goal = "打开网页",
        title = "打开网页",
        summary = "",
        actions = listOf(action)
    )

    @Test
    fun taskTokenBindsTaskAndArguments() {
        val token = tokenPolicy.createTaskToken(plan, now = 1_000, ttlMillis = 500)
        assertTrue(tokenPolicy.isTaskValid(token, plan, now = 1_200))
        assertFalse(tokenPolicy.isTaskValid(token, plan.copy(id = "task-2"), now = 1_200))

        val changed = plan.copy(
            actions = listOf(action.copy(arguments = mapOf("url" to "https://example.org")))
        )
        assertFalse(tokenPolicy.isTaskValid(token, changed, now = 1_200))
    }

    @Test
    fun actionTokenExpiresAndBindsAction() {
        val sensitive = action.copy(
            tool = "send_text",
            risk = RiskLevel.ACTION_APPROVAL
        )
        val sensitivePlan = plan.copy(actions = listOf(sensitive))
        val token = tokenPolicy.createActionToken(
            sensitivePlan,
            sensitive,
            now = 1_000,
            ttlMillis = 500
        )

        assertTrue(tokenPolicy.isActionValid(token, sensitivePlan, sensitive, now = 1_500))
        assertFalse(tokenPolicy.isActionValid(token, sensitivePlan, sensitive, now = 1_501))
        assertFalse(
            tokenPolicy.isActionValid(
                token,
                sensitivePlan,
                sensitive.copy(arguments = mapOf("text" to "changed")),
                now = 1_200
            )
        )
    }

    @Test
    fun tokenCanOnlyBeConsumedOnce() {
        val token = tokenPolicy.createTaskToken(plan, now = 1_000, ttlMillis = 500)
        val consumed = tokenPolicy.consume(token)

        assertFalse(tokenPolicy.isTaskValid(consumed, plan, now = 1_200))
    }
}
