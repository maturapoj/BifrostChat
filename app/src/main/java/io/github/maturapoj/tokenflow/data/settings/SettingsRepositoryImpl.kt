package io.github.maturapoj.tokenflow.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.maturapoj.tokenflow.domain.model.GatewaySettings
import io.github.maturapoj.tokenflow.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Settings in DataStore, API key encrypted with [cipher]. Until the user saves an endpoint,
 * [defaults] apply (debug builds may preload one from local.properties; release has none).
 */
class SettingsRepositoryImpl(
    private val store: DataStore<Preferences>,
    private val cipher: KeystoreCipher,
    private val defaults: GatewaySettings,
) : SettingsRepository {

    override val settings: Flow<GatewaySettings> = store.data.map { prefs ->
        val lastModel = prefs[LAST_MODEL].orEmpty()
        val baseUrl = prefs[BASE_URL] ?: return@map defaults.copy(lastModelId = lastModel)
        val apiKey = prefs[API_KEY]?.let { runCatching { cipher.decrypt(it) }.getOrDefault("") }.orEmpty()
        GatewaySettings(baseUrl, apiKey, lastModel)
    }

    override suspend fun saveEndpoint(baseUrl: String, apiKey: String) {
        store.edit { prefs ->
            prefs[BASE_URL] = baseUrl
            if (apiKey.isEmpty()) prefs.remove(API_KEY) else prefs[API_KEY] = cipher.encrypt(apiKey)
        }
    }

    override suspend fun saveLastModel(modelId: String) {
        store.edit { it[LAST_MODEL] = modelId }
    }

    private companion object {
        val BASE_URL = stringPreferencesKey("base_url")
        val API_KEY = stringPreferencesKey("api_key_encrypted")
        val LAST_MODEL = stringPreferencesKey("last_model_id")
    }
}
