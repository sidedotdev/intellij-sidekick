package com.github.sidedev.sidekick.api.websocket

import com.github.sidedev.sidekick.models.ChatMessageDelta // Specific model import
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers

/**
 * Concrete WebSocket session implementation for handling Flow Events.
 * Expects messages of type ChatMessageDelta.
 */
class FlowEventsSession(
    client: HttpClient, // Provided HttpClient instance
    private val baseUrl: String, // Base URL for the API endpoint
    private val workspaceId: String, // Specific identifiers for the endpoint
    private val flowId: String,
    dispatcher: CoroutineDispatcher = Dispatchers.Default // Optional dispatcher for scope
) : SidekickWebSocketSession(client, dispatcher) { // Pass required params to base

    /**
     * Constructs the specific WebSocket URL for Flow Events.
     */
    override fun buildWsUrl(): String {
        return "$baseUrl/ws/v1/workspaces/$workspaceId/flows/$flowId/events"
            .replace("http://", "ws://") // Convert http(s) to ws(s)
            .replace("https://", "wss://")
    }

    /**
     * Establishes a connection for FlowEvents, setting up handlers for ChatMessageDelta messages.
     * This provides a type-safe public interface, delegating the core connection
     * and listening logic to the generic implementation in the base class.
     *
     * @param onMessage Lambda invoked for each successfully received ChatMessageDelta.
     * @param onError Lambda invoked on connection or message processing errors.
     * @param onClose Lambda invoked when the connection is closed.
     * @return Deferred<Unit> completes successfully on connection, exceptionally on failure.
     */
    fun connect(
        onMessage: suspend (ChatMessageDelta) -> Unit,
        onError: suspend (Throwable) -> Unit,
        onClose: suspend (code: Short, reason: String) -> Unit = { _, _ -> }
    ): Deferred<Unit> {
        println("FlowEventsSession connect called, delegating to generic connect<ChatMessageDelta>.")
        // Call the generic connect method from the base class, specifying the expected message type
        return super.connectGeneric<ChatMessageDelta>(
            onMessage = onMessage,
            onError = onError,
            onClose = onClose
        )
    }
}