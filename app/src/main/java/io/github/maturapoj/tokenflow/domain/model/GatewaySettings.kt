package io.github.maturapoj.tokenflow.domain.model

/** Where to send requests. Any OpenAI-compatible server works; the key may be empty for local ones. */
data class GatewaySettings(
    val baseUrl: String = "",
    val apiKey: String = "",
    /** The model picked most recently, reused for new chats. */
    val lastModelId: String = "",
) {
    val isConfigured: Boolean get() = baseUrl.isNotBlank()

    companion object {
        /**
         * Trims, drops trailing slashes and a trailing `/v1`, since requests append `/v1/...`
         * (people often paste `https://api.openai.com/v1`). Returns null unless it is http(s).
         */
        fun normalizeBaseUrl(raw: String): String? {
            var url = raw.trim().trimEnd('/')
            if (url.endsWith("/v1")) url = url.removeSuffix("/v1").trimEnd('/')
            val scheme = url.substringBefore("://", missingDelimiterValue = "").lowercase()
            val host = url.substringAfter("://", missingDelimiterValue = "")
            return url.takeIf { scheme in setOf("http", "https") && host.isNotBlank() }
        }
    }
}
