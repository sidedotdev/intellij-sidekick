package com.github.sidedev.sidekick.api

import kotlinx.serialization.Serializable

@Serializable
data class WorkspacesResponse(
    val workspaces: List<Workspace>,
)

@Serializable
data class WorkspaceResponse(
    val workspace: Workspace,
)

@Serializable
data class Workspace(
    val id: String,
    val name: String,
    val localRepoDir: String,
    val created: String,
    val updated: String,
)
