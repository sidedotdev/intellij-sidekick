package com.github.sidedev.sidekick.models

import kotlinx.serialization.Serializable

@Serializable
data class ChatCompletionMessage(
    val content: String,
    val role: ChatRole,
    val toolCalls: List<ToolCall>,
    val name: String?,
    val toolCallId: String?,
    val isError: Boolean?,
    val usage: Usage?
)