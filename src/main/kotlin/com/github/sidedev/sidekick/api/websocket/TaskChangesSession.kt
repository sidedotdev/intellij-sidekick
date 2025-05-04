package com.github.sidedev.sidekick.api.websocket

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
// Removed kotlin.reflect.typeOf import as it's no longer needed

/**
 * Concrete WebSocket session implementation for handling Task Changes events.
 * Expects messages of type [TaskChangesPayload].
 */
class TaskChangesSession(
    client: HttpClient, // Provided HttpClient instance
    private val baseUrl: String, // Base URL for the API endpoint
    private val workspaceId: String, // Specific identifier for the endpoint
    dispatcher: CoroutineDispatcher = Dispatchers.Default, // Optional dispatcher for scope
    logger: Logger = logger<TaskChangesSession>(),
) : SidekickWebSocketSession(client, dispatcher, logger) { // Inherit from base class

    /**
     * Constructs the specific WebSocket URL for the task changes endpoint.
     */
    override fun buildWsUrl(): String {
        // Ensure no double slashes if baseUrl ends with /
        val cleanBaseUrl = baseUrl.removeSuffix("/")
        return "$cleanBaseUrl/ws/v1/workspaces/$workspaceId/task_changes"
    }

    /**
     * Establishes the WebSocket connection and starts listening for messages.
     *
     * This function delegates the core connection and message handling logic
     * to the `connectGeneric` method in the base [SidekickWebSocketSession].
     *
     * @param onMessage Callback invoked when a [TaskChangesPayload] message is successfully received and deserialized.
     * @param onError Callback invoked if an error occurs during connection establishment or message processing.
     * @param onClose Callback invoked when the WebSocket connection is closed, providing the close code and reason.
     * @return A [Deferred] that completes when the connection attempt finishes (either successfully establishing the listener or failing).
     *         The completion of the Deferred does not signify the end of the WebSocket session itself, only the setup phase.
     */
    fun connect(
        onMessage: suspend (TaskChangesPayload) -> Unit,
        onError: suspend (Throwable) -> Unit,
        onClose: suspend (code: Short, reason: String) -> Unit = { _, _ -> } // Default no-op onClose
    ): Deferred<Unit> {
        // Delegate connection logic to the generic handler in the base class,
        // specifying TaskChangesPayload as the expected message type.
        return super.connectGeneric<TaskChangesPayload>(
            onMessage = onMessage,
            onError = onError,
            onClose = onClose
            // Type parameter <TaskChangesPayload> handles deserialization type
        )
    }
}