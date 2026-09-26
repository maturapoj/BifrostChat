package io.github.maturapoj.tokenflow.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GatewaySettingsTest {

    @Test fun `trailing slash and v1 are dropped`() {
        assertEquals("https://api.openai.com", GatewaySettings.normalizeBaseUrl(" https://api.openai.com/v1/ "))
        assertEquals("https://openrouter.ai/api", GatewaySettings.normalizeBaseUrl("https://openrouter.ai/api/"))
        assertEquals("http://10.0.2.2:11434", GatewaySettings.normalizeBaseUrl("http://10.0.2.2:11434"))
    }

    @Test fun `only http and https with a host are accepted`() {
        assertNull(GatewaySettings.normalizeBaseUrl("api.openai.com"))
        assertNull(GatewaySettings.normalizeBaseUrl("ftp://example.com"))
        assertNull(GatewaySettings.normalizeBaseUrl("https://"))
        assertNull(GatewaySettings.normalizeBaseUrl(""))
    }
}
