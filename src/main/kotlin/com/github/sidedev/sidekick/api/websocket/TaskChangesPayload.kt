package com.github.sidedev.sidekick.api.websocket

import com.github.sidedev.sidekick.api.Flow
import com.github.sidedev.sidekick.api.TaskResponse
import kotlinx.serialization.Serializable

/**
 * Represents the payload received over the WebSocket connection for task changes.
 *
 * @property tasks A list of task responses, potentially including associated flows.
 * @property lastTaskStreamId The identifier for the last processed stream item, used for potential resynchronization.
 */
@Serializable
data class TaskChangesPayload(
    val tasks: List<TaskResponse>,
    val lastTaskStreamId: String,
)