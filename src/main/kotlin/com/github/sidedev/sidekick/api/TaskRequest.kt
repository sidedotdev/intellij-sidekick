package com.github.sidedev.sidekick.api

import kotlinx.serialization.Serializable

@Serializable
data class TaskRequest(
    val description: String,
    val status: String,
    val agentType: String,
    val flowType: String
)