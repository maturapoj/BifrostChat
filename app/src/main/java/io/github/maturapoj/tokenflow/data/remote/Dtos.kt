package io.github.maturapoj.tokenflow.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Shared JSON config: the gateway adds fields (extra_fields, routing_info, …) we don't model. */
internal val GatewayJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

// --- requests ---

/** Wire format of one OpenAI chat message (`role` is "user" / "assistant"). */
@Serializable
data class MessageDto(val role: String, val content: String)

@Serializable
internal data class ChatRequestDto(
    val model: String,
    val messages: List<MessageDto>,
    val stream: Boolean = true,
    @SerialName("stream_options") val streamOptions: StreamOptionsDto = StreamOptionsDto(),
)

@Serializable
internal data class StreamOptionsDto(@SerialName("include_usage") val includeUsage: Boolean = true)

// --- responses ---

@Serializable
internal data class ModelsResponseDto(val data: List<ModelDto>)

@Serializable
internal data class ModelDto(val id: String)

/** One `chat.completion.chunk`, or an error payload sent mid-stream. */
@Serializable
internal data class ChunkDto(
    val choices: List<ChoiceDto> = emptyList(),
    val usage: UsageDto? = null,
    val error: ErrorDto? = null,
)

@Serializable
internal data class ChoiceDto(
    val delta: DeltaDto? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

/** DeepSeek/Qwen (e.g. via Bifrost or DashScope) send thinking in `reasoning`; other providers use `reasoning_content`. */
@Serializable
internal data class DeltaDto(
    val content: String? = null,
    val reasoning: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
)

@Serializable
internal data class UsageDto(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("completion_tokens_details") val details: CompletionDetailsDto? = null,
)

@Serializable
internal data class CompletionDetailsDto(@SerialName("reasoning_tokens") val reasoningTokens: Int = 0)

@Serializable
internal data class ErrorDto(val message: String? = null)
