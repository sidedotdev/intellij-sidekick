package com.github.sidedev.sidekick.api

data class WorkspaceResponse(
    val workspaces: List<Workspace>
) {
    data class Workspace(
        val id: String,
        val name: String,
        val localRepoDir: String,
        val created: String,
        val updated: String
    )
}