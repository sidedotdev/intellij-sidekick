package com.github.sidedev.sidekick.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.datetime.Instant

@Serializable
data class Task(
    val id: String,
    val workspaceId: String,
    val title: String? = null,
    val description: String,
    val status: TaskStatus,
    val agentType: AgentType,
    val flowType: String? = null,
    val flowOptions: Map<String, JsonElement>? = null,
    val flows: List<Flow>? = null,
    val created: Instant,
    val updated: Instant,
    val archived: Instant? = null
)
