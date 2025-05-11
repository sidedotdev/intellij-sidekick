package com.github.sidedev.sidekick.models.flowEvent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("progress_text")
data class ProgressTextEvent(
    val text: String,
    override val parentId: String
) : FlowEvent