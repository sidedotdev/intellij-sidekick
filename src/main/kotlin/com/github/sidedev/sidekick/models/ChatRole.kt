package com.github.sidedev.sidekick.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ChatRole {
    @SerialName("user")
    USER,
    @SerialName("assistant")
    ASSISTANT,
    @SerialName("system")
    SYSTEM,
    @SerialName("tool")
    TOOL
}