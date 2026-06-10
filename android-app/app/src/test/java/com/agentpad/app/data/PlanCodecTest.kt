package com.agentpad.app.data

import com.agentpad.app.domain.PlannedAction
import com.agentpad.app.domain.RiskLevel
import com.agentpad.app.domain.TaskPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PlanCodecTest {
    @Test
    fun roundTripPreservesPlanSecurityFields() {
        val plan = TaskPlan(
            id = "plan-1",
            goal = "总结报告",
            title = "报告摘要",
            summary = "读取并总结",
            actions = listOf(
                PlannedAction(
                    id = "action-1",
                    title = "上传文档",
                    description = "经逐项审批后发送给模型",
                    tool = "upload_document_for_summary",
                    arguments = mapOf("document" to "report.txt"),
                    risk = RiskLevel.ACTION_APPROVAL,
                    reversible = false
                )
            ),
            stopCondition = "完成摘要",
            maxSteps = 4,
            createdAt = 1234L
        )

        val decoded = PlanCodec.decode(PlanCodec.encode(plan))

        assertNotNull(decoded)
        assertEquals(plan, decoded)
    }

    @Test
    fun blankAndMalformedPayloadsAreRejectedAsMissingPlans() {
        assertEquals(null, PlanCodec.decode(""))
        assertEquals(null, PlanCodec.decode("{not-json"))
    }
}
