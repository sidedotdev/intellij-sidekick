package com.github.sidedev.sidekick.api.websocket

import com.github.sidedev.sidekick.api.response.ApiError
import com.github.sidedev.sidekick.api.response.ApiResponse
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.typeOf

/**
 * Base class for managing WebSocket sessions with shared logic.
 * Owns the CoroutineScope and handles connection, message consumption, sending, and closing.
 */
abstract class SidekickWebSocketSession(
    protected val client: HttpClient,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    protected val logger: Logger = logger<SidekickWebSocketSession>(),
) {
    // Shared JSON configuration
    protected open val json: Json = Json { ignoreUnknownKeys = true }

    // Scope owned by this session instance for managing coroutines (reader job).
    // SupervisorJob ensures failure of one child doesn't cancel others (though we primarily have one main reader job).
    protected val sessionScope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)

    // Handle to the active Ktor WebSocket session, managed internally.
    protected var webSocketSession: DefaultClientWebSocketSession? = null

    // Handle to the coroutine job that reads messages.
    protected var readerJob: Job? = null

    // Subclasses must implement this to provide the specific WebSocket endpoint URL.
    protected abstract fun buildWsUrl(): String

    /**
     * Generic core connection logic. Establishes connection, starts the message listener
     * within the session's scope, and returns a Deferred that signals connection readiness.
     *
     * @param T The reified type of messages expected from the server.
     * @param onMessage Lambda invoked for each successfully received and deserialized message.
     * @param onError Lambda invoked on connection errors or message processing errors.
     * @param onClose Lambda invoked when the connection is closed.
     * @return Deferred<Unit> completes successfully on connection, exceptionally on failure.
     */
    protected inline fun <reified T : Any> connectGeneric(
        crossinline onMessage: suspend (T) -> Unit,
        crossinline onError: suspend (Throwable) -> Unit,
        crossinline onClose: suspend (code: Short, reason: String) -> Unit
    ): Deferred<Unit> {
        // Prevent multiple concurrent connection attempts
        if (readerJob?.isActive == true || webSocketSession?.isActive == true) {
            val errorMessage = "WebSocket session is already active or connection attempt in progress."
            logger.error(errorMessage)
            return CompletableDeferred(Unit).apply { completeExceptionally(IllegalStateException(errorMessage)) }
        }

        val connectionDeferred = CompletableDeferred<Unit>()
        val wsUrl = buildWsUrl()
        val serializer = json.serializersModule.serializer<T>()

        logger.debug("Attempting WebSocket connection to: $wsUrl")
        // Launch the connection and reader logic within the session's scope
        readerJob = sessionScope.launch {
            try {
                // Establish the WebSocket connection using Ktor client
                client.webSocket(
                    method = HttpMethod.Get,
                    host = Url(wsUrl).host,
                    port = Url(wsUrl).port,
                    path = Url(wsUrl).encodedPath
                ) {
                    webSocketSession = this
                    logger.info("WebSocket connected successfully")
                    logger.debug("Session Job: ${this.coroutineContext[Job]}")
                    connectionDeferred.complete(Unit)
                    consumeMessagesGeneric(this, serializer, onMessage, onError, onClose)
                }
            } catch (e: Exception) {
                // Handle exceptions during connection setup or the webSocket block itself
                if (e is CancellationException) {
                    logger.debug("Connection attempt cancelled", e)
                    // If cancelled before connectionDeferred completes, signal failure and call onClose
                    if (!connectionDeferred.isCompleted) {
                        connectionDeferred.completeExceptionally(e)
                        // Provide a default close reason for setup cancellation
                        onClose(CloseReason.Codes.GOING_AWAY.code, "Connection cancelled during setup")
                    }
                    // If connection was established, consumeMessagesGeneric's finally/catch handles onClose
                } else {
                    logger.error("WebSocket connection failed", e)
                    onError(e)
                    if (!connectionDeferred.isCompleted) {
                        connectionDeferred.completeExceptionally(e)
                    }
                }
                webSocketSession = null
            } finally {
                // This block executes after the webSocket session ends (normally, via error, or cancellation)
                logger.debug("WebSocket session coroutine cleanup")
                webSocketSession = null
                // onClose should have been reliably called by consumeMessagesGeneric or catch blocks
            }
        }

        return connectionDeferred
    }

    /**
     * Generic internal method to consume messages from the WebSocket within the session scope.
     * Handles text frames, deserialization using the provided serializer, close frames, pings, and errors.
     */
    protected suspend inline fun <reified T : Any> consumeMessagesGeneric(
        session: DefaultClientWebSocketSession,
        serializer: KSerializer<T>,
        crossinline onMessage: suspend (T) -> Unit,
        crossinline onError: suspend (Throwable) -> Unit,
        crossinline onClose: suspend (code: Short, reason: String) -> Unit
    ) {
        try {
            // Listen indefinitely for incoming frames
            for (frame in session.incoming) {
                if (!session.isActive) { // Extra check for safety
                    logger.debug("Session became inactive during frame processing")
                    break
                }
                when (frame) {
                    is Frame.Text -> {
                        val text = frame.readText()
                        var message: T? = null
                        try {
                            message = json.decodeFromString(serializer, text)
                        } catch (e: Exception) {
                            logger.error("Error processing message type ${typeOf<T>()}: Payload: '$text'", e)
                            onError(e)
                        }
                        try {
                            message?.let { onMessage(it) }
                        } catch (e: Exception) {
                            logger.error("Error in onMessage handler. Message: $message\nonMessage exception:", e)
                            onError(e)
                        }
                    }
                    is Frame.Close -> {
                        val reason = frame.readReason() ?: CloseReason(CLOSE_CODE_NO_STATUS_RCVD, "No close reason provided")
                        logger.info("Received close frame: Code=${reason.code}, Reason='${reason.message}'")
                        onClose(reason.code, reason.message)
                        webSocketSession = null
                        return // Exit the consuming loop
                    }
                    is Frame.Ping -> { // Respond to pings to keep connection alive
                        session.send(Frame.Pong(frame.buffer))
                    }
                    // Potentially handle Frame.Binary or other types
                    else -> logger.debug("Received unhandled frame type: ${frame.frameType.name}")
                }
            }
            // Loop exited normally - typically means the server closed the connection without sending a Close frame.
            logger.info("Incoming channel closed normally (server likely disconnected)")
            onClose(CloseReason.Codes.NORMAL.code, "Incoming channel closed normally")
        } catch (e: CancellationException) {
            // This occurs if the sessionScope or readerJob is cancelled (e.g., by calling close())
            logger.debug("Consume loop cancelled", e)
            onClose(CloseReason.Codes.GOING_AWAY.code, "Connection cancelled by client")
            throw e // Re-throw cancellation to ensure coroutine stops fully
        } catch (e: Exception) {
            // Catch unexpected errors during the consume loop
            logger.error("Error during consume loop", e)
            onError(e)
            // Report an internal error close reason
            onClose(CloseReason.Codes.INTERNAL_ERROR.code, "Consume loop error: ${e.message}")
        } finally {
            // Final cleanup actions after the consume loop exits
            logger.debug("Exiting consume loop")
            webSocketSession = null
        }
    }

    /**
     * Sends a text message over the active WebSocket connection.
     * Returns an ApiResponse indicating success or failure.
     */
    open suspend fun send(message: String): ApiResponse<Unit, ApiError> {
        val session = webSocketSession // Capture session reference for thread safety
        return if (session?.isActive == true) {
            try {
                session.send(Frame.Text(message))
                ApiResponse.Success(Unit) // Indicate success
            } catch (e: Exception) {
                logger.error("Error sending message", e)
                // Consider invoking onError handler as well, depending on desired behavior
                // onError(e)
                ApiResponse.Error(ApiError("Failed to send message: ${e.message}"))
            }
        } else {
            val errorMsg = "Cannot send, WebSocket session is not active."
            logger.error(errorMsg)
            ApiResponse.Error(ApiError(errorMsg))
        }
    }

    /**
     * Gracefully closes the WebSocket connection and cleans up associated resources (scope, jobs).
     */
    open suspend fun close(code: Short = CloseReason.Codes.NORMAL.code, reason: String = "Client closed normally") {
        logger.info("Close called on Session. Code=$code, Reason='$reason'")

        // Prevent closing logic from running multiple times
        if (!sessionScope.isActive) {
            logger.debug("Close called, but session scope is already inactive")
            return
        }

        // 1. Close the Ktor WebSocket session (sends the close frame)
        val session = webSocketSession
        // Clear the reference *before* trying to close to prevent race conditions
        webSocketSession = null
        if (session?.isActive == true) {
            try {
                logger.debug("Requesting Ktor session close...")
                session.close(CloseReason(code, reason))
                logger.debug("Ktor session close requested")
            } catch (e: Exception) {
                // Log error during close, but continue cleanup. Scope cancellation handles reader job.
                logger.error("Exception during explicit Ktor session close", e)
            }
        } else {
            logger.debug("Ktor session was already null or inactive when close was called")
        }

        // 2. Cancel the session's CoroutineScope. This cancels the readerJob and any other
        // potential jobs launched within this scope. Use a specific exception message.
        logger.debug("Cancelling session scope...")
        sessionScope.cancel(CancellationException("Session closed via close() call: $reason"))
    }
}

// Source: https://github.com/Luka967/websocket-close-codes
const val CLOSE_CODE_NO_STATUS_RCVD: Short = 1005
