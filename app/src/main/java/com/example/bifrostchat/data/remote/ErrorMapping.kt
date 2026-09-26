package com.example.bifrostchat.data.remote

import com.example.bifrostchat.domain.model.ChatError
import com.example.bifrostchat.domain.model.ChatException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import java.io.IOException

/** Maps transport failures to domain errors; cancellation passes through untouched. */
internal fun Throwable.toChatException(): Throwable = when (this) {
    is CancellationException, is ChatException -> this
    else -> ChatException(toChatError())
}

internal fun Throwable.toChatError(): ChatError = when (this) {
    is ChatException -> error
    is BifrostHttpException -> when (code) {
        401, 403 -> ChatError.Unauthorized
        429 -> ChatError.RateLimited
        else -> ChatError.Server(code, body.take(MAX_DETAIL))
    }
    is BifrostException -> ChatError.Gateway(message.orEmpty().take(MAX_DETAIL))
    is SerializationException -> ChatError.Unknown("Unexpected response from the gateway")
    is IOException -> ChatError.Network
    else -> ChatError.Unknown(message ?: toString())
}

private const val MAX_DETAIL = 300
