package com.github.sidedev.sidekick.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AgentType {
    @SerialName("human")
    HUMAN,
    @SerialName("llm")
    LLM,
    @SerialName("none")
    NONE
}