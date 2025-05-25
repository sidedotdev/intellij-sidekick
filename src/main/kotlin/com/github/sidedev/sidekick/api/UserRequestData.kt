package com.github.sidedev.sidekick.api

import kotlinx.serialization.Serializable

@Serializable
data class UserResponse(
    val content: String?,
    val approved: Boolean?
)

@Serializable
data class UserResponsePayload(
    val userResponse: UserResponse
)

@Serializable
data class ActionResult(
    val content: String?,
    val approved: Boolean?
)
