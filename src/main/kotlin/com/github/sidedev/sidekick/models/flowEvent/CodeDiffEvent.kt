package com.github.sidedev.sidekick.models.flowEvent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("code_diff")
data class CodeDiffEvent(
    val subflowId: String,
    val diff: String
) : FlowEvent {
    override val parentId: String
        get() = subflowId
}