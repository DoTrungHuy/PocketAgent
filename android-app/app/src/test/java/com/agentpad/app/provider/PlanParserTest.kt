package com.agentpad.app.provider

import com.agentpad.app.domain.RiskLevel
import com.agentpad.app.policy.ApprovalPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PlanParserTest {
    private val parser = PlanParser(ApprovalPolicy())

    @Test
    fun parsesKnownToolAndUpgradesRisk() {
        val plan = parser.parse(
            "总结文件",
            """
                {
                  "title": "总结报告",
                  "summary": "读取并总结用户选择的文件",
                  "actions": [
                    {
                      "title": "上传并总结",
                      "description": "将文件内容发送到模型",
                      "tool": "upload_document_for_summary",
                      "arguments": {},
                      "risk": "READ_ONLY",
                      "reversible": false
                    }
                  ]
                }
            """.trimIndent()
        )

        assertEquals(1, plan.actions.size)
        assertEquals(RiskLevel.ACTION_APPROVAL, plan.actions.single().risk)
    }

    @Test
    fun rejectsUnknownTools() {
        assertThrows(IllegalArgumentException::class.java) {
            parser.parse(
                "执行未知程序",
                """
                    {
                      "title": "危险计划",
                      "summary": "",
                      "actions": [
                        {
                          "title": "运行",
                          "description": "",
                          "tool": "download_and_execute",
                          "arguments": {},
                          "risk": "READ_ONLY",
                          "reversible": false
                        }
                      ]
                    }
                """.trimIndent()
            )
        }
    }

    @Test
    fun rejectsForbiddenTools() {
        assertThrows(IllegalArgumentException::class.java) {
            parser.parse(
                "付款",
                """
                    {
                      "title": "付款",
                      "summary": "",
                      "actions": [
                        {
                          "title": "支付",
                          "description": "",
                          "tool": "payment",
                          "arguments": {},
                          "risk": "READ_ONLY",
                          "reversible": false
                        }
                      ]
                    }
                """.trimIndent()
            )
        }
    }
}
