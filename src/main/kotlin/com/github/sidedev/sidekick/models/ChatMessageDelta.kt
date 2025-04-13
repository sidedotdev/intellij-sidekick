package com.github.sidedev.sidekick.models

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessageDelta(
    val role: ChatRole,
    val content: String,
    val toolCalls: List<ToolCall>,
    val usage: Usage
)