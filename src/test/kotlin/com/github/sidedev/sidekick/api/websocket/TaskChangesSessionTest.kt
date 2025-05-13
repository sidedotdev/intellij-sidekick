package com.github.sidedev.sidekick.api.websocket

import com.github.sidedev.sidekick.api.AgentType
import com.github.sidedev.sidekick.api.Flow
import com.github.sidedev.sidekick.api.SidekickService
import com.github.sidedev.sidekick.api.Task
import com.github.sidedev.sidekick.api.TaskResponse
import com.github.sidedev.sidekick.api.TaskStatus
import com.intellij.openapi.diagnostic.Logger
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.WebSocket
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import kotlin.concurrent.thread

@OptIn(ExperimentalCoroutinesApi::class)
class TaskChangesSessionTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var mockWebServer: MockWebServer
    private lateinit var sidekickService: SidekickService
    private var serverListener: WebSocketRecorder = WebSocketRecorder("server")
    private var receivedPayload: TaskChangesPayload? = null
    private var errorOccurred = false
    private var connectionClosed = false
    private val mockLogger = mockk<Logger>(relaxed = true)

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        receivedPayload = null
        errorOccurred = false
        connectionClosed = false

        sidekickService = SidekickService(
            baseUrl = mockWebServer.url("/").toString(),
            dispatcher = testDispatcher,
            logger = mockLogger,
        )

        // Mock logger behavior (optional, copied from template)
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
    fun `test session connection and message handling`() = runTest(testDispatcher) {
        // Given: A test payload and WebSocket upgrade response
        mockWebServer.enqueue(MockResponse().withWebSocketUpgrade(serverListener))
        // Use correct constructors based on actual data classes
        val testFlow = Flow(
            id = "flow-1",
            workspaceId = "test-workspace",
            type = "test-flow-type",
            parentId = "task-1",
            status = "running"
        )
        val testTask = Task(
            id = "task-1",
            workspaceId = "test-workspace",
            description = "Test Task Description",
            status = TaskStatus.IN_PROGRESS,
            agentType = AgentType.LLM,
            created = Instant.DISTANT_PAST, // Use a fixed time for testing
            updated = Instant.DISTANT_PAST
        )
        val testTaskResponse = TaskResponse(
            task = testTask,
            flows = listOf(testFlow)
        )
        val testPayload = TaskChangesPayload(
            tasks = listOf(testTaskResponse),
            lastTaskStreamId = "stream-id-123"
        )

        // When: Connect to WebSocket
        val taskChangesSessionResult = sidekickService
            .connectToTaskChanges(
                workspaceId = "test-workspace",
                onMessage = { payload -> receivedPayload = payload },
                onError = { errorOccurred = true },
                onClose = { _, _ -> connectionClosed = true },
            )

        // Then: Connection is successful
        assertTrue(taskChangesSessionResult.isSuccess)
        val taskChangesSession = taskChangesSessionResult.getOrThrow()
        assertFalse(errorOccurred)
        assertFalse(connectionClosed)
        assertNull(receivedPayload) // No message received yet

        // When: Server sends a message
        val serverMessage = Json.encodeToString(testPayload)
        val serverWebsocket: WebSocket = serverListener.assertOpen()

        serverWebsocket.send(serverMessage)
        waitForMessageProcessing()

        // Then: Message is received and deserialized by client
        assertNotNull("onMessage should have been called", receivedPayload)
        assertEquals(testPayload, receivedPayload)
        assertFalse("onError should not have been called", errorOccurred) // No error should occur during message processing

        // Clean up
        taskChangesSession.close(1000, "Test completed")
    }

    @Test
    @Ignore("Closing the mock websocket doesn't seem to do send a close frame, so we'll omit this test case for now")
    fun `test close handling`() = runTest(testDispatcher) {
        var closeReason: String? = null
        var closeCode: Short? = null
        var onErrorCalled = false

        // Given: A WebSocket upgrade response
        mockWebServer.enqueue(MockResponse().withWebSocketUpgrade(serverListener))

        // When: Connect to WebSocket with close handler
        val sessionResult = sidekickService
            .connectToTaskChanges(
                workspaceId = "test-workspace",
                onMessage = { fail("onMessage should not be called in close test") },
                onError = { onErrorCalled = true; fail("onError should not be called in normal close test: $it") },
                onClose = { code, reason ->
                    closeCode = code
                    closeReason = reason
                },
            )
        val session = sessionResult.getOrThrow()

        // Then: Connection is successful initially
        assertNull(closeReason)

        // When: Server closes connection
        val server: WebSocket = serverListener.assertOpen()
        server.close(1002, "Server initiated close")
        waitForMessageProcessing()

        // Then: Close handler is called
        assertFalse("onError should not have been called", onErrorCalled)
        assertEquals(1002.toShort(), closeCode)
        assertEquals("Server initiated close", closeReason)

        // Clean up (calling close on an already closed session should be safe)
        session.close(1000, "Test completed")
    }

    @Test
    fun `test connection failure`() = runTest(testDispatcher) {
        // suppress noisy (and expected) error log
        // suppress noisy (and expected) error log
        every { mockLogger.error(any<String>(), any<Throwable>()) } returns Unit
        var capturedError: Throwable? = null

        // Given: A failing WebSocket upgrade response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401) // Simulate an auth error, for example
                .setBody("Unauthorized"),
        )

        // When: Try to connect
        val sessionResult = sidekickService.connectToTaskChanges(
            workspaceId = "test-workspace",
            onMessage = { fail("onMessage should not be called") },
            onError = { capturedError = it }, // Expect onError to be called
            onClose = { _, _ -> fail("onClose should not be called") },
        )

        // Then: Connection fails with error and onError is called
        assertTrue("connectToTaskChanges should return Failure", sessionResult.isFailure)
        assertNotNull("onError should have been called", capturedError)
        // Optionally assert on the specific exception type if needed
        // assert(capturedError is SomeExpectedException)
    }

    private fun waitForMessageProcessing() {
        // HACK: Thread.sleep is required instead of advanceUntilIdle because the
        // mock web server is not in any coroutine scope. Unfortunately, we can't
        // work around this any other way without a lot of work
        Thread.sleep(200)
    }
}