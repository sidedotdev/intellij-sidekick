package com.github.sidedev.sidekick.models.flowEvent

import com.github.sidedev.sidekick.models.ChatMessageDelta
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("chat_message_delta")
data class ChatMessageDeltaEvent(
    val flowActionId: String,
    val chatMessageDelta: ChatMessageDelta
) : FlowEvent {
    override val parentId: String
        get() = flowActionId
}