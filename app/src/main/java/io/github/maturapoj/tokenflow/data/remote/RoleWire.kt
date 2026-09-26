package io.github.maturapoj.tokenflow.data.remote

import io.github.maturapoj.tokenflow.domain.model.Role

/** OpenAI wire name for a role; the messages table stores the same value. */
internal val Role.wire: String
    get() = when (this) {
        Role.User -> "user"
        Role.Assistant -> "assistant"
    }

internal fun roleFromWire(value: String): Role = if (value == "user") Role.User else Role.Assistant
