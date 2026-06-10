package com.agentpad.app.ui

import android.content.Context
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.agentpad.app.MainActivity
import com.agentpad.app.data.SettingsStore
import com.agentpad.app.domain.ProviderSettings
import com.agentpad.app.security.SecureApiKeyStore
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompactWorkspaceTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun configuredModelCanEnterWorkspaceWithoutNestedScrollCrash() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runBlocking {
            SettingsStore(context).saveProvider(
                ProviderSettings(
                    providerId = "deepseek",
                    endpoint = "https://api.deepseek.com/chat/completions",
                    model = "deepseek-chat"
                )
            )
        }
        SecureApiKeyStore(context).save("test-key-for-compose-render")

        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.onNodeWithText("AgentPad").assertExists()
            composeRule.onNodeWithText("DeepSeek · deepseek-chat", substring = true).assertExists()
        }
    }
}
