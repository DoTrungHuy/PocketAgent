package com.agentpad.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.agentpad.app.domain.ProviderSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.pocketAgentDataStore by preferencesDataStore(name = "pocketagent_settings")

enum class ThemePreference {
    LIGHT,
    DARK
}

data class AppPreferences(
    val providerSettings: ProviderSettings = ProviderSettings(),
    val theme: ThemePreference = ThemePreference.LIGHT,
    val privacyMode: Boolean = false
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val providerId = stringPreferencesKey("provider_id")
        val endpoint = stringPreferencesKey("endpoint")
        val model = stringPreferencesKey("model")
        val streamingEnabled = booleanPreferencesKey("streaming_enabled")
        val visionEndpoint = stringPreferencesKey("vision_endpoint")
        val visionModel = stringPreferencesKey("vision_model")
        val theme = stringPreferencesKey("theme")
        val privacyMode = booleanPreferencesKey("privacy_mode")
    }

    val preferences: Flow<AppPreferences> = context.pocketAgentDataStore.data.map { values ->
        AppPreferences(
            providerSettings = ProviderSettings(
                providerId = values[Keys.providerId] ?: "deepseek",
                endpoint = values[Keys.endpoint] ?: "https://api.deepseek.com/chat/completions",
                model = values[Keys.model] ?: "deepseek-v4-flash",
                streamingEnabled = values[Keys.streamingEnabled] ?: false,
                visionEndpoint = values[Keys.visionEndpoint] ?: "",
                visionModel = values[Keys.visionModel] ?: ""
            ),
            theme = runCatching {
                ThemePreference.valueOf(values[Keys.theme] ?: ThemePreference.LIGHT.name)
            }.getOrDefault(ThemePreference.LIGHT),
            privacyMode = values[Keys.privacyMode] ?: false
        )
    }

    val providerSettings: Flow<ProviderSettings> = preferences.map { it.providerSettings }

    suspend fun saveProvider(settings: ProviderSettings) {
        context.pocketAgentDataStore.edit { values ->
            values[Keys.providerId] = settings.providerId.trim()
            values[Keys.endpoint] = settings.endpoint.trim()
            values[Keys.model] = settings.model.trim()
            values[Keys.streamingEnabled] = settings.streamingEnabled
            values[Keys.visionEndpoint] = settings.visionEndpoint.trim()
            values[Keys.visionModel] = settings.visionModel.trim()
        }
    }

    suspend fun setTheme(theme: ThemePreference) {
        context.pocketAgentDataStore.edit { it[Keys.theme] = theme.name }
    }

    suspend fun setPrivacyMode(enabled: Boolean) {
        context.pocketAgentDataStore.edit { it[Keys.privacyMode] = enabled }
    }

    suspend fun clear() {
        context.pocketAgentDataStore.edit { it.clear() }
    }
}
