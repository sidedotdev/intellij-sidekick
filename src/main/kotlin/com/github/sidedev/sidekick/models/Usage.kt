package com.github.sidedev.sidekick.models

import kotlinx.serialization.Serializable

@Serializable
data class Usage(
    val inputTokens: Int?,
    val outputTokens: Int?
)