package com.github.sidedev.sidekick.api

import kotlinx.serialization.Serializable

@Serializable
data class WorkspaceRequest(
    val name: String,
    val localRepoDir: String
)