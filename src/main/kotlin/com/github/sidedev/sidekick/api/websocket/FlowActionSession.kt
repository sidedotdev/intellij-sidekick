package com.github.sidedev.sidekick.api.websocket

import com.github.sidedev.sidekick.models.FlowAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers

/**
 * Concrete WebSocket session implementation for handling Flow Actions.
 * Expects messages of type FlowAction.
 */
class FlowActionSession(
    client: HttpClient,
    private val baseUrl: String,
    private val workspaceId: String,
    private val flowId: String,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    logger: Logger = logger<FlowActionSession>(),
) : SidekickWebSocketSession(client, dispatcher, logger) {

    /**
     * Constructs the specific WebSocket URL for Flow Actions.
     */
    override fun buildWsUrl(): String {
        return "$baseUrl/ws/v1/workspaces/$workspaceId/flows/$flowId/action_changes_ws"
            .replace("http://", "ws://")
            .replace("https://", "wss://")
    }

    /**
     * Establishes a connection for Flow Actions, setting up handlers for FlowAction messages.
     * This provides a type-safe public interface, delegating the core connection
     * and listening logic to the generic implementation in the base class.
     *
     * @param onMessage Lambda invoked for each successfully received FlowAction.
     * @param onError Lambda invoked on connection or message processing errors.
     * @param onClose Lambda invoked when the connection is closed.
     * @return Deferred<Unit> completes successfully on connection, exceptionally on failure.
     */
    fun connect(
        onMessage: suspend (FlowAction) -> Unit,
        onError: suspend (Throwable) -> Unit,
        onClose: suspend (code: Short, reason: String) -> Unit = { _, _ -> }
    ): Deferred<Unit> {
        return super.connectGeneric<FlowAction>(
            onMessage = onMessage,
            onError = onError,
            onClose = onClose
        )
    }
}