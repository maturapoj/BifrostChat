package io.github.maturapoj.tokenflow.domain.repository

import io.github.maturapoj.tokenflow.domain.model.GatewaySettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<GatewaySettings>

    /** Stores the endpoint; the API key is kept encrypted. */
    suspend fun saveEndpoint(baseUrl: String, apiKey: String)

    suspend fun saveLastModel(modelId: String)
}
