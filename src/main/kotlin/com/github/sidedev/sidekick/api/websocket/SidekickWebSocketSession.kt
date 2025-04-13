package com.github.sidedev.sidekick.api.websocket

import com.github.sidedev.sidekick.api.response.ApiError
import com.github.sidedev.sidekick.api.response.ApiResponse
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import io.ktor.websocket.CloseReason

abstract class SidekickWebSocketSession {
    protected var session: DefaultClientWebSocketSession? = null
    protected val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    abstract suspend fun send(message: String): ApiResponse<Unit, ApiError>

    abstract suspend fun close(code: Short = CloseReason.Codes.NORMAL.code, reason: String = "Client closed normally")
}