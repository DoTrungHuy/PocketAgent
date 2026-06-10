package com.agentpad.app.provider

import com.agentpad.app.domain.PlannedAction
import com.agentpad.app.domain.RiskLevel
import com.agentpad.app.domain.TaskPlan
import com.agentpad.app.policy.ApprovalPolicy
import org.json.JSONObject

class PlanParser(private val policy: ApprovalPolicy) {
    fun parse(goal: String, raw: String): TaskPlan {
        val json = extractJson(raw)
        val root = JSONObject(json)
        val actionsJson = root.optJSONArray("actions")
        val actions = buildList {
            if (actionsJson != null) {
                for (index in 0 until minOf(actionsJson.length(), MAX_ACTIONS)) {
                    val item = actionsJson.optJSONObject(index) ?: continue
                    val tool = item.optString("tool").trim()
                    if (tool !in policy.knownTools()) continue
                    val arguments = item.optJSONObject("arguments")
                    val argumentMap = buildMap {
                        if (arguments != null) {
                            arguments.keys().forEach { key ->
                                put(key, arguments.optString(key))
                            }
                        }
                    }
                    add(
                        policy.normalize(
                            PlannedAction(
                                title = item.optString("title", tool).take(MAX_TEXT),
                                description = item.optString("description").take(MAX_TEXT),
                                tool = tool,
                                arguments = argumentMap,
                                risk = parseRisk(item.optString("risk")),
                                reversible = item.optBoolean("reversible", false)
                            )
                        )
                    )
                }
            }
        }
        require(actions.isNotEmpty()) { "模型没有返回可识别的安全步骤" }
        require(actions.none { it.risk == RiskLevel.FORBIDDEN }) { "计划包含永久禁止的操作" }
        return TaskPlan(
            goal = goal.take(MAX_GOAL),
            title = root.optString("title", "新任务").take(MAX_TEXT),
            summary = root.optString("summary").take(MAX_TEXT),
            actions = actions,
            stopCondition = root.optString(
                "stopCondition",
                "目标完成、用户取消或出现无法安全处理的错误"
            ).take(MAX_TEXT)
        )
    }

    private fun extractJson(raw: String): String {
        val trimmed = raw.trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        require(start >= 0 && end > start) { "模型未返回 JSON 任务计划" }
        return trimmed.substring(start, end + 1)
    }

    private fun parseRisk(value: String): RiskLevel =
        runCatching { RiskLevel.valueOf(value.uppercase()) }.getOrDefault(RiskLevel.ACTION_APPROVAL)

    private companion object {
        const val MAX_ACTIONS = 8
        const val MAX_TEXT = 500
        const val MAX_GOAL = 2_000
    }
}
