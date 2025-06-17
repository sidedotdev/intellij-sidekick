package com.github.sidedev.sidekick.api

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

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
@OptIn(ExperimentalSerializationApi::class)
data class UserRequestActionResult  constructor(
    @JsonNames("Content") // alternative for legacy json
    val content: String?,
    @JsonNames("Approved") // alternative for legacy json
    val approved: Boolean?
)
