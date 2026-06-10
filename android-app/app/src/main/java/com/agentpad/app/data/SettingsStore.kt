package com.agentpad.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.agentpad.app.domain.ProviderSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.agentPadDataStore by preferencesDataStore(name = "agentpad_settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val providerId = stringPreferencesKey("provider_id")
        val endpoint = stringPreferencesKey("endpoint")
        val model = stringPreferencesKey("model")
        val visionEndpoint = stringPreferencesKey("vision_endpoint")
        val visionModel = stringPreferencesKey("vision_model")
    }

    val providerSettings: Flow<ProviderSettings> = context.agentPadDataStore.data.map { values ->
        ProviderSettings(
            providerId = values[Keys.providerId] ?: "deepseek",
            endpoint = values[Keys.endpoint] ?: "https://api.deepseek.com/chat/completions",
            model = values[Keys.model] ?: "",
            visionEndpoint = values[Keys.visionEndpoint] ?: "",
            visionModel = values[Keys.visionModel] ?: ""
        )
    }

    suspend fun save(settings: ProviderSettings) {
        context.agentPadDataStore.edit { values ->
            values[Keys.providerId] = settings.providerId.trim()
            values[Keys.endpoint] = settings.endpoint.trim()
            values[Keys.model] = settings.model.trim()
            values[Keys.visionEndpoint] = settings.visionEndpoint.trim()
            values[Keys.visionModel] = settings.visionModel.trim()
        }
    }

    suspend fun clear() {
        context.agentPadDataStore.edit { values ->
            values.remove(Keys.providerId)
            values.remove(Keys.endpoint)
            values.remove(Keys.model)
            values.remove(Keys.visionEndpoint)
            values.remove(Keys.visionModel)
        }
    }
}
