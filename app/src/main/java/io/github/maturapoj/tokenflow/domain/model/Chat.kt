package io.github.maturapoj.tokenflow.domain.model

enum class Role { User, Assistant }

data class ChatMessage(val role: Role, val content: String)
