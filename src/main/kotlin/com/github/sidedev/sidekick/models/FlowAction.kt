package com.github.sidedev.sidekick.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class FlowAction(
    val id: String,
    val flowId: String,
    val subflowId: String?,
    val workspaceId: String,
    val created: Instant,
    val updated: Instant,
    val actionType: String,
    val actionParams: Map<String, JsonElement>,
    val actionStatus: ActionStatus,
    val actionResult: String,
    val isHumanAction: Boolean
)