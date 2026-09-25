package com.example.bifrostchat.domain.model

/** A model the gateway serves, e.g. id `dashscope/qwen3.7-plus` → provider `dashscope`, name `qwen3.7-plus`. */
data class LlmModel(val id: String, val provider: String, val name: String)

data class ModelGroup(val provider: String, val models: List<LlmModel>)
