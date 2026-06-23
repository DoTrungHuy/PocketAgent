package com.agentpad.app.provider

import com.agentpad.app.domain.PlannedAction
import com.agentpad.app.domain.RiskLevel
import com.agentpad.app.domain.TaskPlan
import com.agentpad.app.policy.ApprovalPolicy
import org.json.JSONException
import org.json.JSONObject

class PlanParser(private val policy: ApprovalPolicy) {
    fun parse(goal: String, raw: String): TaskPlan {
        try {
            val root = JSONObject(extractJson(raw))
            val actionsJson = root.getJSONArray("actions")
            require(actionsJson.length() in 1..MAX_ACTIONS) { "Plan must contain 1 to $MAX_ACTIONS actions" }
            val actions = buildList {
                for (index in 0 until actionsJson.length()) {
                    val item = actionsJson.getJSONObject(index)
                    val tool = item.getString("tool").trim()
                    require(tool.isNotEmpty()) { "Plan action is missing a tool name" }
                    require(tool in policy.knownTools()) { "Plan contains unknown tool: $tool" }
                    val arguments = when {
                        !item.has("arguments") || item.isNull("arguments") -> null
                        else -> item.getJSONObject("arguments")
                    }
                    val argumentMap = buildMap {
                        arguments?.keys()?.forEach { key ->
                            val value = arguments.get(key)
                            require(value is String || value is Number || value is Boolean) {
                                "Tool arguments must be strings, numbers, or booleans"
                            }
                            put(key, value.toString())
                        }
                    }
                    val action = policy.normalize(
                        PlannedAction(
                            title = item.optString("title", tool).take(MAX_TEXT),
                            description = item.optString("description").take(MAX_TEXT),
                            tool = tool,
                            arguments = argumentMap,
                            risk = parseRisk(item.optString("risk")),
                            reversible = item.optBoolean("reversible", false)
                        )
                    )
                    require(action.risk != RiskLevel.FORBIDDEN) { "Plan contains a forbidden action: $tool" }
                    add(action)
                }
            }
            return TaskPlan(
                goal = goal.take(MAX_GOAL),
                title = root.optString("title", "New task").take(MAX_TEXT),
                summary = root.optString("summary").take(MAX_TEXT),
                actions = actions,
                stopCondition = root.optString("stopCondition", "Goal completed, user cancelled, or unsafe request detected.").take(MAX_TEXT)
            )
        } catch (failure: JSONException) {
            throw IllegalArgumentException("Model returned an invalid task plan", failure)
        }
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
        require(start >= 0 && end > start) { "Model did not return a JSON task plan" }
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