package com.github.sidedev.sidekick.models

import kotlinx.serialization.Serializable

@Serializable
data class ChatCompletionChoice(
    val content: String,
    val role: ChatRole,
    val toolCalls: List<ToolCall>,
    val name: String?,
    val toolCallId: String?,
    val isError: Boolean?,
    val usage: Usage?,
    val stopReason: String,
    val model: String?,
    val provider: String?
)