package com.github.sidedev.sidekick.api.websocket

import com.github.sidedev.sidekick.api.SidekickService
import com.github.sidedev.sidekick.models.ActionStatus
import com.github.sidedev.sidekick.models.FlowAction
import com.intellij.openapi.diagnostic.Logger
import io.mockk.mockk
import io.mockk.every
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
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
class FlowActionSessionTest {
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
        thread(isDaemon = true) {
            try {
                mockWebServer.shutdown()
            } catch (e: Throwable) {
                println("Warning: mock web server shutdown failed: $e")
            }
        }
    }

    @Test
    fun testSessionConnectionAndMessageHandling() = runTest(testDispatcher) {
        // Given: A test message and WebSocket upgrade response
        mockWebServer.enqueue(MockResponse().withWebSocketUpgrade(serverListener))
        val now = Clock.System.now()
        val testMessage = FlowAction(
            id = "test-action-id",
            flowId = "test-flow",
            workspaceId = "test-workspace",
            created = now,
            updated = now,
            actionType = "test-action",
            actionParams = mapOf("key" to JsonPrimitive("value")),
            actionStatus = ActionStatus.STARTED,
            actionResult = "Test result",
            isHumanAction = false
        )

        // When: Connect to WebSocket
        val flowActionSession = sidekickService
            .connectToFlowActions(
                workspaceId = "test-workspace",
                flowId = "test-flow",
                onMessage = { messageReceived = true },
                onError = { errorOccurred = true },
                onClose = { _, _ -> connectionClosed = true },
            ).getOrThrow()
        flowActionSession.send("doesn't matter, mock web server doesn't care")

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
        flowActionSession.close(1000, "Test completed")
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
            .connectToFlowActions(
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
        val sessionResult = sidekickService.connectToFlowActions(
            workspaceId = "test-workspace",
            flowId = "test-flow",
            onMessage = { },
            onError = { },
        )

        // Then: Connection fails with error
        assertTrue(sessionResult.isFailure)
    }

    private fun waitForMessageProcessing() {
        // HACK: Thread.sleep is required instead of advanceUntilIdle because the
        // mock web server is not in any coroutine scope. Unfortunately, we can't
        // work around this any other way without a lot of work
        Thread.sleep(200)
    }
}