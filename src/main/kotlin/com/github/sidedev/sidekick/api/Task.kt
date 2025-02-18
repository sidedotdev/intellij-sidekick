package com.github.sidedev.sidekick.api

import kotlinx.serialization.Serializable

@Serializable
data class Task(
    val id: String,
    val workspaceId: String,
    val status: String,
    val agentType: String,
    val flowType: String,
    val description: String,
    val createdAt: String,
    val updatedAt: String
)