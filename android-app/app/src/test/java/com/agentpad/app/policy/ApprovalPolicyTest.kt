package com.agentpad.app.policy

import com.agentpad.app.domain.PlannedAction
import com.agentpad.app.domain.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ApprovalPolicyTest {
    private val policy = ApprovalPolicy()

    @Test
    fun modelCannotDowngradeSensitiveAction() {
        val action = PlannedAction(
            title = "上传文档",
            description = "发送到模型",
            tool = "upload_document_for_summary",
            risk = RiskLevel.READ_ONLY,
            reversible = false
        )

        assertEquals(RiskLevel.ACTION_APPROVAL, policy.normalize(action).risk)
    }

    @Test
    fun unknownToolIsForbidden() {
        assertEquals(RiskLevel.FORBIDDEN, policy.riskFor("download_and_execute"))
    }

    @Test
    fun digestChangesWhenArgumentsChange() {
        val first = PlannedAction(
            title = "打开网页",
            description = "",
            tool = "open_url",
            arguments = mapOf("url" to "https://example.com"),
            risk = RiskLevel.TASK_APPROVAL,
            reversible = true
        )
        val second = first.copy(arguments = mapOf("url" to "https://example.org"))

        assertNotEquals(policy.argumentDigest(first), policy.argumentDigest(second))
    }
}
