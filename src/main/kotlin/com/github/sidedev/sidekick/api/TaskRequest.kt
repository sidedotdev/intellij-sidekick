package com.github.sidedev.sidekick.api

data class TaskRequest(
    val description: String,
    val status: String,
    val agentType: String,
    val flowType: String
)