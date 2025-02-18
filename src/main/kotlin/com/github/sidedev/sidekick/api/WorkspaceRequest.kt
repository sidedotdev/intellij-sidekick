package com.github.sidedev.sidekick.api

data class WorkspaceRequest(
    val name: String,
    val localRepoDir: String
)