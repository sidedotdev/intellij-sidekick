package com.github.sidedev.sidekick.api

import kotlinx.serialization.Serializable

@Serializable
data class FlowOptions(
    val determineRequirements: Boolean,
    val planningPrompt: String? = null,
    val envType: String? = null,
)

@Serializable
data class TaskRequest(
    val description: String,
    val status: String,
    val agentType: String,
    val flowType: String,
    val flowOptions: FlowOptions,
)
