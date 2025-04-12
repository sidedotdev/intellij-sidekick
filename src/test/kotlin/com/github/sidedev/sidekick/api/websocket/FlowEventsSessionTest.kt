package com.github.sidedev.sidekick.api.websocket

import com.github.sidedev.sidekick.api.SidekickService
import com.github.sidedev.sidekick.models.*
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test


@OptIn(ExperimentalCoroutinesApi::class)
class FlowEventsSessionTest {
    // tests run concurrent coroutines, so we need to use the standard test
    // dispatcher
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var mockWebServer: MockWebServer
    private lateinit var sidekickService: SidekickService
    private var serverListener: WebSocketRecorder = WebSocketRecorder("server")
    private var messageReceived = false
    private var errorOccurred = false
    private var connectionClosed = false


    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        sidekickService = SidekickService(
            baseUrl = mockWebServer.url("/").toString(),
            dispatcher = testDispatcher,
        )

        messageReceived = false
        errorOccurred = false
        connectionClosed = false
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun testSessionConnectionAndMessageHandling() = runTest(testDispatcher) {
        // Given: A test message and WebSocket upgrade response
        mockWebServer.enqueue(MockResponse().withWebSocketUpgrade(serverListener))
        val testMessage = ChatMessageDelta(
            role = ChatRole.ASSISTANT,
            content = "Test message",
            toolCalls = emptyList(),
            usage = Usage(inputTokens = 10, outputTokens = 20)
        )

        // When: Connect to WebSocket
        val flowEventsSession = sidekickService.connectToFlowEvents(
            workspaceId = "test-workspace",
            flowId = "test-flow",
            onMessage = { println("got onmessage"); messageReceived = true },
            onError = { println("got error"); errorOccurred = true },
            onClose = { _, _ -> println("got close"); connectionClosed = true }
        ).getOrThrow()
        println("after connect to flow events")
        flowEventsSession.send("doesn't matter, mock web server doesn't care")

        // Then: Connection is successful
        assertFalse(errorOccurred)
        assertFalse(connectionClosed)
        assertFalse(messageReceived)
        println("after assertions")

        // When: Server sends a message
        val serverMessage = Json.encodeToString(testMessage)
        val serverWebsocket: WebSocket = serverListener.assertOpen()
        println("after assert open")

        serverWebsocket.send(serverMessage)
        println("after send wtf")

        // Then: Message is received by client
        waitForMessageProcessing()
        assertTrue(messageReceived)

        /*
        // When: Server closes connection with error
        serverWebsocket.close(1002, "Protocol error")

        // Then: Error handler is called
        waitForMessageProcessing()
        assertTrue(connectionClosed)
         */

        // Clean up
        flowEventsSession.close()
    }

    @Test
    @Ignore("Closing the mock websocket doesn't seem to do send a close frame, so we'll omit this test case for now")
    fun testCloseHandling() = runTest(testDispatcher) {
        var closeReason: String? = null
        var closeCode: Short? = null

        // Given: A WebSocket upgrade response
        mockWebServer.enqueue(MockResponse().withWebSocketUpgrade(serverListener))

        // When: Connect to WebSocket with error handler
        val session = sidekickService.connectToFlowEvents(
            workspaceId = "test-workspace",
            flowId = "test-flow",
            onMessage = {},
            onError = {},
            onClose = { code, reason -> closeCode = code; closeReason = reason }
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
        session.close()
    }

    @Test
    fun testConnectionFailure() = runTest(testDispatcher) {
        // Given: A failing WebSocket upgrade response
        mockWebServer.enqueue(MockResponse()
            .setResponseCode(500)
            .setBody("Internal Server Error"))

        // When: Try to connect and handle error
        val sessionResult = sidekickService.connectToFlowEvents(
            workspaceId = "test-workspace",
            flowId = "test-flow",
            onMessage = {},
            onError = {},
            onClose = { _, _ -> }
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