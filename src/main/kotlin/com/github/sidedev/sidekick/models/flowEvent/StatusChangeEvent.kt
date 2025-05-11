package com.github.sidedev.sidekick.models.flowEvent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("status_change")
data class StatusChangeEvent(
    val status: String,
    override val parentId: String
) : FlowEvent