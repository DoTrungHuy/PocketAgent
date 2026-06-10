package com.agentpad.app.provider

import com.agentpad.app.domain.MessageKind
import com.agentpad.app.domain.ThreadMessage

class ThreadContextPolicy(
    private val maxMessages: Int = 60,
    private val maxCharacters: Int = 48_000
) {
    fun needsCompression(messages: List<ThreadMessage>): Boolean {
        val requestMessages = requestMessages(messages)
        return requestMessages.size > maxMessages ||
            requestMessages.sumOf { it.content.length } > maxCharacters
    }

    fun requestMessages(messages: List<ThreadMessage>): List<ThreadMessage> {
        val checkpointIndex = messages.indexOfLast { it.kind == MessageKind.CONTEXT_SUMMARY }
        if (checkpointIndex < 0) return messages
        return listOf(messages[checkpointIndex]) + messages.drop(checkpointIndex + 1)
    }
}
