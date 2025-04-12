package com.github.sidedev.sidekick.api.websocket

import com.github.sidedev.sidekick.api.response.ApiError
import com.github.sidedev.sidekick.api.response.ApiResponse
import com.github.sidedev.sidekick.models.*
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FlowEventsSession(
    private val client: HttpClient,
    private val baseUrl: String,
    private val workspaceId: String,
    private val flowId: String,
) : SidekickWebSocketSession() {
    override suspend fun send(message: String): ApiResponse<Unit, ApiError> = send_legacy(message)

    override suspend fun close(code: Short, reason: String) {
        session?.close(CloseReason(code, reason))
        session = null
    }

    private var messageHandler: (suspend (ChatMessageDelta) -> Unit)? = null
    private var errorHandler: (suspend (Throwable) -> Unit)? = null
    private var closeHandler: (suspend (code: Short, reason: String) -> Unit)? = null

    suspend fun connect(
        onMessage: suspend (ChatMessageDelta) -> Unit,
        onError: suspend (Throwable) -> Unit = {},
        onClose: suspend (code: Short, reason: String) -> Unit = { _, _ -> },
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): Result<CompletableDeferred<Unit>> = runCatching {
        messageHandler = onMessage
        errorHandler = onError
        closeHandler = onClose

        val wsUrl = "$baseUrl/ws/v1/workspaces/$workspaceId/flows/$flowId/events"
            .replace("http://", "ws://")
            .replace("https://", "wss://")

        val consumeDeferred = CompletableDeferred<Unit>()
        val localSession = client.webSocketSession(
            method = HttpMethod.Get,
            host = Url(wsUrl).host,
            port = Url(wsUrl).port,
            path = Url(wsUrl).encodedPath
        )
        println("right after WebSocket connected")
        CoroutineScope(dispatcher).launch {
            println("outer: WebSocket connected")
            consumeDeferred.complete(Unit)

            println("starting consume")
            try {
                for (frame in localSession.incoming) {
                    //val text = message.text
                    println("frame received: $frame")
                    when (frame) {
                        is Frame.Text -> {
                            val text = frame.readText()
                            messageHandler!!.invoke(
                                json
                                    .decodeFromString<ChatMessageDelta>(text)
                            )
                            println("messageHandler invoked with text: $text")
                            println("messageHandler: $messageHandler")
                            //println("v2 flow on text message: $text")
                        }
                        is Frame.Binary -> {
                        }
                        is Frame.Ping -> {
                            localSession.send(Frame.Pong(frame.buffer))
                        }
                        is Frame.Close -> {
                            val reason = frame.readReason() ?: CloseReason(-1, "Unknown: Missing close reason")
                            onClose(reason.code, reason.message)
                        } else -> {
                            println("v2 flow on other message")
                        }
                    }
                }
                println("deferred complete")
            } catch (e: Exception) {
                consumeDeferred.completeExceptionally(e)
                println("deferred complete exception: $e")
            } finally {
                println("deferred complete finally")
            }
            println("beyond the async block")
        }
        println("beyond the async block more")
        return@runCatching consumeDeferred
    }
}