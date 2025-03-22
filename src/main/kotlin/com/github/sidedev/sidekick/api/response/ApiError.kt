package com.github.sidedev.sidekick.api.response

import kotlinx.serialization.Serializable

@Serializable
data class ApiError(
    val error: String,
)
