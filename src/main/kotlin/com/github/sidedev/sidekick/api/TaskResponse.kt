package com.github.sidedev.sidekick.api

import com.github.sidedev.sidekick.api.Flow // Added import
import kotlinx.serialization.Serializable

@Serializable
data class TasksResponse(
    val tasks: List<Task>,
)

/**
 * Represents a single task, potentially including associated flows.
 *
 * @property task The core task data.
 * @property flows An optional list of flows associated with this task update.
 */
@Serializable
data class TaskResponse(
    val task: Task,
    val flows: List<Flow>? = null // Added optional flows field
)
