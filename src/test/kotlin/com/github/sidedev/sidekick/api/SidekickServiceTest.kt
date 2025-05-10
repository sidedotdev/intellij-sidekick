package com.github.sidedev.sidekick.api

import com.github.sidedev.sidekick.api.response.ApiError
import com.github.sidedev.sidekick.api.response.ApiResponse
import com.intellij.openapi.diagnostic.Logger
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

class SidekickServiceTest {
    private val testJson = Json {
        ignoreUnknownKeys = true
        prettyPrint = false // prettyPrint is true in service, but not essential for test payload matching
        // encodeDefaults = false by default, meaning nulls might be omitted. This is usually fine.
    }
    private val defaultBaseUrl = "http://localhost:8855" // Matches service default
    @OptIn(ExperimentalCoroutinesApi::class)
    private val testDispatcher = UnconfinedTestDispatcher()

    @Test
    fun `completeFlowAction success - returns Unit on 200 OK with {}`() = runTest {
        val workspaceId = "ws-123"
        val flowActionId = "fa-456"
        val userResponse = UserResponse(content = "Approved by user", approved = true)
        val payload = UserResponsePayload(userResponse = userResponse)

        val mockEngine = MockEngine { request ->
            assertEquals(
                "$defaultBaseUrl/api/v1/workspaces/$workspaceId/flow_actions/$flowActionId/complete",
                request.url.toString()
            )
            assertEquals(HttpMethod.Post, request.method)
            assertEquals(ContentType.Application.Json, request.body.contentType)

            val actualRequestBody = testJson.decodeFromString<UserResponsePayload>((request.body as TextContent).text)
            assertEquals(payload, actualRequestBody)

            respond(
                content = ByteReadChannel("""{}""".toByteArray()), // Expecting Unit to deserialize from {}
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }

        val service = SidekickService(
            baseUrl = defaultBaseUrl,
            engine = mockEngine,
            dispatcher = testDispatcher,
            logger = mockk<Logger>(relaxed = true),
        )
        val response = service.completeFlowAction(workspaceId, flowActionId, payload)

        assertTrue("Response should be Success", response is ApiResponse.Success)
        assertEquals(Unit, (response as ApiResponse.Success).data)
    }

    @Test
    fun `completeFlowAction API error - returns ApiError`() = runTest {
        val workspaceId = "ws-789"
        val flowActionId = "fa-012"
        val payload = UserResponsePayload(UserResponse(content = "Attempt with error", approved = false))
        val errorJson = """{"error": "Action failed validation"}"""
        val expectedApiError = ApiError("Action failed validation")

        val mockEngine = MockEngine {
            respond(
                content = ByteReadChannel(errorJson.toByteArray()),
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val service = SidekickService(
            baseUrl = defaultBaseUrl,
            engine = mockEngine,
            dispatcher = testDispatcher,
            logger = mockk<Logger>(relaxed = true),
        )
        val response = service.completeFlowAction(workspaceId, flowActionId, payload)

        assertTrue("Response should be Error", response is ApiResponse.Error)
        assertEquals(expectedApiError, (response as ApiResponse.Error).error)
    }

    @Test
    fun `completeFlowAction network error - returns ApiError with exception message`() = runTest {
        val workspaceId = "ws-net-err"
        val flowActionId = "fa-net-err"
        val payload = UserResponsePayload(UserResponse(content = "Network test", approved = null))
        val exceptionMessage = "Simulated network failure"

        val mockEngine = MockEngine {
            throw RuntimeException(exceptionMessage)
        }
        val service = SidekickService(
            baseUrl = defaultBaseUrl,
            engine = mockEngine,
            dispatcher = testDispatcher,
            logger = mockk<Logger>(relaxed = true),
        )
        val response = service.completeFlowAction(workspaceId, flowActionId, payload)

        assertTrue("Response should be Error for network exception", response is ApiResponse.Error)
        assertEquals(ApiError(exceptionMessage), (response as ApiResponse.Error).error)
    }
}