package com.github.sidedev.sidekick.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class TaskStatus {
    @SerialName("drafting")
    DRAFTING,
    @SerialName("to_do")
    TO_DO,
    @SerialName("blocked")
    BLOCKED,
    @SerialName("in_progress")
    IN_PROGRESS,
    @SerialName("complete")
    COMPLETE,
    @SerialName("canceled")
    CANCELED,
    @SerialName("failed")
    FAILED,
}