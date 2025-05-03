package com.github.sidedev.sidekick.api

import kotlinx.serialization.Serializable

@Serializable
data class Flow(
    val id: String,
    val workspaceId: String,
    val type: String,
    val parentId: String,
    val status: String,
    val description: String? = null
)