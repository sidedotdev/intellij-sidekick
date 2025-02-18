package com.github.sidedev.sidekick.api

import com.fasterxml.jackson.annotation.JsonProperty

data class WorkspaceResponse(
    @JsonProperty("workspaces")
    val workspaces: List<Workspace>
) {
    data class Workspace(
        val id: String,
        val name: String,
        @JsonProperty("localRepoDir")
        val localRepoDir: String,
        val created: String,
        val updated: String
    )
}