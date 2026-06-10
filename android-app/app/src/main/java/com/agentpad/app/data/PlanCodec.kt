package com.agentpad.app.data

import com.agentpad.app.domain.PlannedAction
import com.agentpad.app.domain.RiskLevel
import com.agentpad.app.domain.TaskPlan
import org.json.JSONArray
import org.json.JSONObject

object PlanCodec {
    fun encode(plan: TaskPlan): String = JSONObject()
        .put("id", plan.id)
        .put("goal", plan.goal)
        .put("title", plan.title)
        .put("summary", plan.summary)
        .put("stopCondition", plan.stopCondition)
        .put("maxSteps", plan.maxSteps)
        .put("createdAt", plan.createdAt)
        .put(
            "actions",
            JSONArray().apply {
                plan.actions.forEach { action ->
                    put(
                        JSONObject()
                            .put("id", action.id)
                            .put("title", action.title)
                            .put("description", action.description)
                            .put("tool", action.tool)
                            .put("risk", action.risk.name)
                            .put("reversible", action.reversible)
                            .put("arguments", JSONObject(action.arguments))
                    )
                }
            }
        )
        .toString()

    fun decode(value: String): TaskPlan? {
        if (value.isBlank()) return null
        return runCatching {
            val root = JSONObject(value)
            val actionsJson = root.getJSONArray("actions")
            val actions = buildList {
                for (index in 0 until actionsJson.length()) {
                    val item = actionsJson.getJSONObject(index)
                    val argsJson = item.optJSONObject("arguments") ?: JSONObject()
                    val arguments = buildMap {
                        argsJson.keys().forEach { key -> put(key, argsJson.optString(key)) }
                    }
                    add(
                        PlannedAction(
                            id = item.getString("id"),
                            title = item.getString("title"),
                            description = item.optString("description"),
                            tool = item.getString("tool"),
                            arguments = arguments,
                            risk = RiskLevel.valueOf(item.getString("risk")),
                            reversible = item.optBoolean("reversible")
                        )
                    )
                }
            }
            TaskPlan(
                id = root.getString("id"),
                goal = root.getString("goal"),
                title = root.getString("title"),
                summary = root.optString("summary"),
                actions = actions,
                stopCondition = root.optString(
                    "stopCondition",
                    "目标完成、用户取消、达到限制或出现无法安全处理的错误"
                ),
                maxSteps = root.optInt("maxSteps", 8),
                createdAt = root.optLong("createdAt", System.currentTimeMillis())
            )
        }.getOrNull()
    }
}
