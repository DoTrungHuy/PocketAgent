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
            "summarize document",
            """
                {
                  "title": "Summarize report",
                  "summary": "Read and summarize the authorized file",
                  "actions": [
                    {
                      "title": "Upload and summarize",
                      "description": "Send document text to the model",
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
                "run unknown program",
                """
                    {
                      "title": "Unsafe plan",
                      "summary": "",
                      "actions": [
                        {
                          "title": "Run",
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
                "pay money",
                """
                    {
                      "title": "Payment",
                      "summary": "",
                      "actions": [
                        {
                          "title": "Pay",
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

    @Test
    fun rejectsMixedSafeAndUnknownTools() {
        assertThrows(IllegalArgumentException::class.java) {
            parser.parse(
                "inspect then run unknown program",
                """
                    {
                      "actions": [
                        {"tool": "inspect_task", "arguments": {}},
                        {"tool": "download_and_execute", "arguments": {}}
                      ]
                    }
                """.trimIndent()
            )
        }
    }

    @Test
    fun rejectsPlansOverStepLimit() {
        val actions = (1..9).joinToString(",") {
            """{"tool":"inspect_task","arguments":{}}"""
        }
        assertThrows(IllegalArgumentException::class.java) {
            parser.parse("too many steps", """{"actions":[$actions]}""")
        }
    }

    @Test
    fun rejectsMalformedAction() {
        assertThrows(IllegalArgumentException::class.java) {
            parser.parse("bad format", """{"actions":["not-an-object"]}""")
        }
    }

    @Test
    fun parsesJsonCodeFence() {
        val plan = parser.parse(
            "inspect task",
            """
                ```json
                {
                  "actions": [
                    {"tool": "inspect_task", "arguments": {}, "risk": "READ_ONLY"}
                  ]
                }
                ```
            """.trimIndent()
        )

        assertEquals("inspect_task", plan.actions.single().tool)
    }
}