package com.github.sidedev.sidekick.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
enum class ActionStatus {
    PENDING,
    STARTED,
    COMPLETE,
    FAILED
}

@Serializable
data class FlowAction(
    val id: String,
    val flowId: String,
    val workspaceId: String,
    val created: Instant,
    val updated: Instant,
    val subflow: String,
    val subflowDescription: String? = null,
    val actionType: String,
    val actionParams: Map<String, JsonElement>,
    val actionStatus: ActionStatus,
    val actionResult: String,
    val isHumanAction: Boolean
)