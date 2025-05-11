package com.github.sidedev.sidekick.models.flowEvent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("end_stream")
data class EndStreamEvent(
    override val parentId: String
) : FlowEvent