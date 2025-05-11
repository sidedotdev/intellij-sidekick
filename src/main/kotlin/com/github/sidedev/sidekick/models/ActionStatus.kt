package com.github.sidedev.sidekick.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
enum class ActionStatus {
    @SerialName("pending")
    PENDING,
    @SerialName("started")
    STARTED,
    @SerialName("complete")
    COMPLETE,
    @SerialName("failed")
    FAILED;

    fun isNonTerminal(): Boolean = this == PENDING || this == STARTED
}