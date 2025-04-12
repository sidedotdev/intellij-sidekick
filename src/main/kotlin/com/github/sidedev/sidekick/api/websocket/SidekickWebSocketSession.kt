package com.github.sidedev.sidekick.api.websocket

import com.github.sidedev.sidekick.api.response.ApiError
import com.github.sidedev.sidekick.api.response.ApiResponse
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

abstract class SidekickWebSocketSession {
    protected var session: DefaultClientWebSocketSession? = null
    protected val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    protected suspend fun consume(
        clientSession: DefaultClientWebSocketSession,
        onMessage: suspend (String) -> Unit,
        onError: suspend (Throwable) -> Unit,
        onClose: suspend (code: Int, reason: String) -> Unit
    ) {
        session = clientSession
        println("consuming websocket... ")
        try {
            //for (frame in clientSession.incoming) {
            //    println("V2 got frame... " + frame.toString());
            //}
            clientSession.incoming.consumeAsFlow()
                //.map {
                //    println("got frame... " + it.toString());
                //}
                .filterIsInstance<Frame.Text>()
                .onEach { println("read text + on message... "); onMessage(it
                    .readText()) }
                .catch { e ->
                    println("consume error 2")
                    println(e)
                    onError(e)
                }
            println("done consuming websocket... ")
        } catch (e: Exception) {
            println("error consuming websocket... ")
            println(e)
            onError(e)
        } finally {
            val closeReason = session?.closeReason?.await()
            println("closing: " + (closeReason?.toString() ?: "unknown"))
            onClose(closeReason?.code?.toInt() ?: -1, closeReason?.message ?: "Unknown")
            println("onclose ran")
            session = null
        }
    }

    suspend fun close() {
        session?.close()
        session = null
    }

    internal suspend fun send(message: String): ApiResponse<Unit, ApiError> =
        try {
            session?.send(message)
            ApiResponse.Success(Unit)
        } catch (e: Exception) {
            ApiResponse.Error(ApiError(e.message ?: "Failed to send message"))
        }
}