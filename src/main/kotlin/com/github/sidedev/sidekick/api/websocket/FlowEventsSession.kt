package com.github.sidedev.sidekick.api.websocket

import com.github.sidedev.sidekick.api.response.ApiError
import com.github.sidedev.sidekick.api.response.ApiResponse
import com.github.sidedev.sidekick.models.ChatMessageDelta
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.utils.io.CancellationException
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

class FlowEventsSession(
    private val client: HttpClient,
    private val baseUrl: String,
    private val workspaceId: String,
    private val flowId: String,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : SidekickWebSocketSession() {
    // Session owns its scope with SupervisorJob for independent job handling
    private val sessionScope = CoroutineScope(SupervisorJob() + dispatcher)
    private var webSocketSession: DefaultClientWebSocketSession? = null
    private var readerJob: Job? = null

    override suspend fun send(message: String): ApiResponse<Unit, ApiError> {
        val session = webSocketSession
        return if (session?.isActive == true) {
            try {
                println("Sending: $message")
                session.send(Frame.Text(message))
                ApiResponse.Success(Unit)
            } catch (e: Exception) {
                println("Error sending message: $e")
                errorHandler?.invoke(e)
                ApiResponse.Error(ApiError("Failed to send message: ${e.message}"))
            }
        } else {
            println("Cannot send, WebSocket session is not active.")
            ApiResponse.Error(ApiError("WebSocket session is not active"))
        }
    }

    override suspend fun close(code: Short, reason: String) {
        println("Close called on FlowEventsSession")
        sessionScope.cancel(CancellationException("Session closed by client: $reason"))

        val session = webSocketSession
        if (session?.isActive == true) {
            try {
                session.close(CloseReason(code, reason))
                println("WebSocket session closed")
            } catch (e: Exception) {
                println("Error during WebSocket close: $e")
            }
        }
        webSocketSession = null
    }

    private var messageHandler: (suspend (ChatMessageDelta) -> Unit)? = null
    private var errorHandler: (suspend (Throwable) -> Unit)? = null
    private var closeHandler: (suspend (code: Short, reason: String) -> Unit)? = null

    private suspend fun consumeMessages(session: DefaultClientWebSocketSession) {
        try {
            for (frame in session.incoming) {
                when (frame) {
                    is Frame.Text -> {
                        val text = frame.readText()
                        println("Received text frame: $text")
                        try {
                            messageHandler?.invoke(
                                json.decodeFromString<ChatMessageDelta>(text),
                            )
                        } catch (e: Exception) {
                            println("Error processing message: $e")
                            errorHandler?.invoke(e)
                        }
                    }
                    is Frame.Close -> {
                        val reason = frame.readReason() ?: CloseReason(-1, "Unknown: Missing close reason")
                        println("Received close frame: Code=${reason.code}, Reason='${reason.message}'")
                        closeHandler?.invoke(reason.code, reason.message)
                        webSocketSession = null
                        return
                    }
                    is Frame.Ping -> {
                        session.send(Frame.Pong(frame.buffer))
                    }
                    else -> println("Received other frame type: ${frame.frameType.name}")
                }
            }
            println("Incoming channel closed normally.")
            closeHandler?.invoke(CloseReason.Codes.NORMAL.code, "Incoming channel closed")
        } catch (e: CancellationException) {
            println("Consume loop cancelled: $e")
            closeHandler?.invoke(CloseReason.Codes.GOING_AWAY.code, "Connection cancelled")
            throw e
        } catch (e: Exception) {
            println("Error during consume loop: $e")
            errorHandler?.invoke(e)
            closeHandler?.invoke(CloseReason.Codes.INTERNAL_ERROR.code, "Consume loop error: ${e.message}")
        } finally {
            println("Exiting consume loop.")
            webSocketSession = null
        }
    }

    fun connect(
        onMessage: suspend (ChatMessageDelta) -> Unit,
        onError: suspend (Throwable) -> Unit = {},
        onClose: suspend (code: Short, reason: String) -> Unit = { _, _ -> },
    ): Deferred<Unit> {
        messageHandler = onMessage
        errorHandler = onError
        closeHandler = onClose

        val wsUrl = "$baseUrl/ws/v1/workspaces/$workspaceId/flows/$flowId/events"
            .replace("http://", "ws://")
            .replace("https://", "wss://")

        val connectionDeferred = CompletableDeferred<Unit>()

        readerJob = sessionScope.launch {
            try {
                client.webSocket(
                    method = HttpMethod.Get,
                    host = Url(wsUrl).host,
                    port = Url(wsUrl).port,
                    path = Url(wsUrl).encodedPath,
                ) {
                    webSocketSession = this
                    println("WebSocket connected")
                    connectionDeferred.complete(Unit)
                    consumeMessages(this)
                }
            } catch (e: Exception) {
                println("WebSocket connection failed: $e")
                errorHandler?.invoke(e)
                connectionDeferred.completeExceptionally(e)
                webSocketSession = null
            }
        }

        return connectionDeferred
    }
}
