package com.agentpad.app

import android.app.Application
import com.agentpad.app.data.PocketAgentRepository
import com.agentpad.app.data.SettingsStore
import com.agentpad.app.data.local.PocketAgentDatabase
import com.agentpad.app.diagnostics.CrashReporter
import com.agentpad.app.policy.ApprovalPolicy
import com.agentpad.app.provider.OpenAiCompatibleClient
import com.agentpad.app.security.SecureApiKeyStore
import com.agentpad.app.tool.AndroidToolExecutor

class PocketAgentApplication : Application() {
    val crashReporter by lazy { CrashReporter(this) }
    val database by lazy { PocketAgentDatabase.get(this) }
    val settingsStore by lazy { SettingsStore(this) }
    val secureApiKeyStore by lazy { SecureApiKeyStore(this) }
    val approvalPolicy by lazy { ApprovalPolicy() }
    val providerClient by lazy { OpenAiCompatibleClient(approvalPolicy) }
    val toolExecutor by lazy { AndroidToolExecutor(this) }
    val repository by lazy {
        PocketAgentRepository(
            threadDao = database.threadDao(),
            auditDao = database.auditDao(),
            documentDao = database.documentDao()
        )
    }

    override fun onCreate() {
        super.onCreate()
        crashReporter.install()
    }
}
