package com.github.sidedev.sidekick.api

import kotlinx.serialization.Serializable

@Serializable
data class TasksResponse(
    val tasks: List<Task>
)

@Serializable
data class TaskResponse(
    val task: Task
)
