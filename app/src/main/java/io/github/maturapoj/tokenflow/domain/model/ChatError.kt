package io.github.maturapoj.tokenflow.domain.model

/** Why a request or stream failed, independent of how the data layer found out. */
sealed interface ChatError {
    /** No endpoint has been set up yet. */
    data object NotConfigured : ChatError

    /** No connection, DNS failure, timeout, or the socket dropped mid-stream. */
    data object Network : ChatError

    /** 401/403: the API key was rejected. */
    data object Unauthorized : ChatError

    /** 429: too many requests. */
    data object RateLimited : ChatError

    /** Any other non-2xx response. */
    data class Server(val code: Int, val detail: String) : ChatError

    /** The gateway or model reported an error inside the stream. */
    data class Gateway(val detail: String) : ChatError

    data class Unknown(val detail: String) : ChatError
}

/** Thrown across layer boundaries so callers can react to a [ChatError] rather than a transport exception. */
class ChatException(val error: ChatError) : Exception(error.toString())

/** The [ChatError] behind any failure; anything unexpected becomes [ChatError.Unknown]. */
fun Throwable.asChatError(): ChatError = (this as? ChatException)?.error ?: ChatError.Unknown(message ?: toString())
