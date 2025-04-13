package com.github.sidedev.sidekick.api.websocket

import com.github.sidedev.sidekick.api.SidekickService
import com.github.sidedev.sidekick.models.ChatMessageDelta
import com.github.sidedev.sidekick.models.ChatRole
import com.github.sidedev.sidekick.models.Usage
import com.intellij.openapi.diagnostic.Logger
import io.mockk.mockk
import io.mockk.every
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.WebSocket
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import kotlin.concurrent.thread

@OptIn(ExperimentalCoroutinesApi::class)
class FlowEventsSessionTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var mockWebServer: MockWebServer
    private lateinit var sidekickService: SidekickService
    private var serverListener: WebSocketRecorder = WebSocketRecorder("server")
    private var messageReceived = false
    private var errorOccurred = false
    private var connectionClosed = false
    private val mockLogger = mockk<Logger>(relaxed = true)

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        messageReceived = false
        errorOccurred = false
        connectionClosed = false

        sidekickService = SidekickService(
            baseUrl = mockWebServer.url("/").toString(),
            dispatcher = testDispatcher,
            logger = mockLogger,
        )

        // don't log debug/info during tests normally
        /*
        every { mockLogger.debug(any<String>()) } answers {
            println("[DEBUG]: ${invocation.args}")
        }
        every { mockLogger.debug(any<String>(), any<Throwable>()) } answers {
            println("[DEBUG]: ${invocation.args}")
        }
        every { mockLogger.info(any<String>()) } answers {
            println("[INFO]: ${invocation.args}")
        }
        every { mockLogger.info(any<String>(), any<Throwable>()) } answers {
            println("[INFO]: ${invocation.args}")
        }
        */

        every { mockLogger.warn(any<String>(), any<Throwable>()) } answers {
            println("[WARN]: ${invocation.args}")
        }
        every { mockLogger.error(any<String>()) } answers {
            println("[ERROR]: ${invocation.args}")
        }
        every { mockLogger.error(any<String>(), any<Throwable>()) } answers {
            println("[ERROR]: ${invocation.args}")
        }

    }

    @After
    fun tearDown() {
        // ending the test will free this up anyway, so no need to wait for graceful shutdown
        thread(isDaemon = true) {
            try {
                mockWebServer.shutdown()
            } catch (e: Throwable) {
                // no need to fail tests if shutdown fails
                println("Warning: mock web server shutdown failed: $e")
            }
        }
    }

    @Test
    fun testSessionConnectionAndMessageHandling() = runTest(testDispatcher) {
        // Given: A test message and WebSocket upgrade response
        mockWebServer.enqueue(MockResponse().withWebSocketUpgrade(serverListener))
        val testMessage = ChatMessageDelta(
            role = ChatRole.ASSISTANT,
            content = "Test message",
            toolCalls = emptyList(),
            usage = Usage(inputTokens = 10, outputTokens = 20),
        )

        // When: Connect to WebSocket
        val flowEventsSession = sidekickService
            .connectToFlowEvents(
                workspaceId = "test-workspace",
                flowId = "test-flow",
                onMessage = { messageReceived = true },
                onError = { errorOccurred = true },
                onClose = { _, _ -> connectionClosed = true },
            ).getOrThrow()
        flowEventsSession.send("doesn't matter, mock web server doesn't care")

        // Then: Connection is successful
        assertFalse(errorOccurred)
        assertFalse(connectionClosed)
        assertFalse(messageReceived)

        // When: Server sends a message
        val serverMessage = Json.encodeToString(testMessage)
        val serverWebsocket: WebSocket = serverListener.assertOpen()

        serverWebsocket.send(serverMessage)

        // Then: Message is received by client
        waitForMessageProcessing()
        assertTrue(messageReceived)

        // Clean up
        flowEventsSession.close(1000, "Test completed")
    }

    @Test
    @Ignore("Closing the mock websocket doesn't seem to do send a close frame, so we'll omit this test case for now")
    fun testCloseHandling() = runTest(testDispatcher) {
        var closeReason: String? = null
        var closeCode: Short? = null

        // Given: A WebSocket upgrade response
        mockWebServer.enqueue(MockResponse().withWebSocketUpgrade(serverListener))

        // When: Connect to WebSocket with error handler
        val session = sidekickService
            .connectToFlowEvents(
                workspaceId = "test-workspace",
                flowId = "test-flow",
                onMessage = {},
                onError = {},
                onClose = { code, reason ->
                    closeCode = code
                    closeReason = reason
                },
            ).getOrThrow()

        // Then: Connection is successful initially
        assertNull(closeReason)

        // When: Server closes connection
        val server: WebSocket = serverListener.assertOpen()
        server.close(1002, "Some close reason")

        // Then: Close handler is called
        waitForMessageProcessing()
        assertEquals(1002, closeCode)
        assertEquals("Some close reason", closeReason)

        // Clean up
        session.close(1000, "Test completed")
    }

    @Test
    fun testConnectionFailure() = runTest(testDispatcher) {
        // suppress noisy (and expected) error log
        every { mockLogger.error(any<String>(), any<Throwable>()) } returns Unit

        // Given: A failing WebSocket upgrade response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"),
        )

        // When: Try to connect and handle error
        val sessionResult = sidekickService.connectToFlowEvents(
            workspaceId = "test-workspace",
            flowId = "test-flow",
            onMessage = {},
            onError = {},
            onClose = { _, _ -> },
        )

        // Then: Connection fails with error
        assertTrue(sessionResult.isFailure)
    }

    private fun waitForMessageProcessing() {
        // while we can check server.queueSize(), that's not good enough.
        // we don't have a great way to check if messages queued to be sent
        // from the server have been actually sent yet for code managed outside
        // the coroutine scope, so we use a hacky sleep instead (note: delay
        // won't work here, since we need another thread to make progress and
        // delays are instant in tests). alternatively, we could wait until the
        // asserted value becomes true
        Thread.sleep(200)
    }
}
