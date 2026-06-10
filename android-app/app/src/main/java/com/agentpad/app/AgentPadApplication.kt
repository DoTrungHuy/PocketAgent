package com.agentpad.app

import android.app.Application
import com.agentpad.app.data.AgentPadRepository
import com.agentpad.app.data.SettingsStore
import com.agentpad.app.data.local.AgentPadDatabase
import com.agentpad.app.policy.ApprovalPolicy
import com.agentpad.app.provider.OpenAiCompatibleClient
import com.agentpad.app.security.SecureApiKeyStore
import com.agentpad.app.tool.AndroidToolExecutor

class AgentPadApplication : Application() {
    val database by lazy { AgentPadDatabase.get(this) }
    val settingsStore by lazy { SettingsStore(this) }
    val secureApiKeyStore by lazy { SecureApiKeyStore(this) }
    val approvalPolicy by lazy { ApprovalPolicy() }
    val providerClient by lazy { OpenAiCompatibleClient(approvalPolicy) }
    val toolExecutor by lazy { AndroidToolExecutor(this) }
    val repository by lazy {
        AgentPadRepository(
            taskDao = database.taskDao(),
            auditDao = database.auditDao()
        )
    }
}
