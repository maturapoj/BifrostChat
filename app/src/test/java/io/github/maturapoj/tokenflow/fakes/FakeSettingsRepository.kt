package io.github.maturapoj.tokenflow.fakes

import io.github.maturapoj.tokenflow.domain.model.GatewaySettings
import io.github.maturapoj.tokenflow.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSettingsRepository(
    initial: GatewaySettings = GatewaySettings(baseUrl = "https://example.test", apiKey = "k"),
) : SettingsRepository {
    override val settings = MutableStateFlow(initial)

    override suspend fun saveEndpoint(baseUrl: String, apiKey: String) {
        settings.value = settings.value.copy(baseUrl = baseUrl, apiKey = apiKey)
    }

    override suspend fun saveLastModel(modelId: String) {
        settings.value = settings.value.copy(lastModelId = modelId)
    }
}
