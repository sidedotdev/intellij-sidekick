package com.github.sidedev.sidekick.api

import com.github.sidedev.sidekick.api.response.ApiError
import com.github.sidedev.sidekick.api.response.ApiResponse
import com.github.sidedev.sidekick.api.response.DeleteTaskResponse
import com.github.sidedev.sidekick.api.websocket.*
import com.github.sidedev.sidekick.models.ChatMessageDelta
import com.github.sidedev.sidekick.models.FlowAction
import com.github.sidedev.sidekick.models.flowEvent.FlowEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json

class SidekickService(
    private val baseUrl: String = "http://localhost:8855",
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val logger: Logger = logger<SidekickService>(),
    engine: HttpClientEngine = CIO.create() // Added for testability
) {
    private val client = HttpClient(engine) { // Use the provided or default engine
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    prettyPrint = true
                },
            )
        }
        install(WebSockets) {
            pingInterval = 20_000
        }
    }

    private suspend inline fun <reified Req, reified Res : Any> request(
        method: HttpMethod,
        path: String,
        requestBody: Req? = null,
    ): ApiResponse<Res, ApiError> = try {
        val response = client.request("$baseUrl/api/v1$path") {
            this.method = method
            if (requestBody != null) {
                contentType(ContentType.Application.Json) // Explicitly set Content-Type
                setBody(requestBody)
            }
        }

        if (response.status.isSuccess()) {
            ApiResponse.Success(response.body())
        } else {
            val errorMessage = try {
                response.body<ApiError>()
            } catch (e: Exception) {
                ApiError("Error ${response.status.value}: ${response.status.description}")
            }
            ApiResponse.Error(errorMessage)
        }
    } catch (e: Exception) {
        ApiResponse.Error(ApiError(e.message ?: "Unknown error"))
    }

    suspend fun getWorkspaces(): ApiResponse<List<Workspace>, ApiError> = request<Unit, WorkspacesResponse>(
        method = HttpMethod.Get,
        path = "/workspaces",
    ).map { it.workspaces }

    suspend fun createWorkspace(workspaceRequest: WorkspaceRequest): ApiResponse<Workspace, ApiError> =
        request<WorkspaceRequest, WorkspaceResponse>(
            method = HttpMethod.Post,
            path = "/workspaces",
            requestBody = workspaceRequest,
        ).map { it.workspace }

    suspend fun getTasks(workspaceId: String, statuses: String? = null): ApiResponse<List<Task>, ApiError> {
        val path = StringBuilder("/workspaces/$workspaceId/tasks")
        if (statuses != null) {
            path.append("?statuses=$statuses")
        }

        return request<Unit, TasksResponse>(
            method = HttpMethod.Get,
            path = path.toString(),
        ).map { it.tasks }
    }

    suspend fun createTask(workspaceId: String, taskRequest: TaskRequest): ApiResponse<Task, ApiError> =
        request<TaskRequest, TaskResponse>(
            method = HttpMethod.Post,
            path = "/workspaces/$workspaceId/tasks/",
            requestBody = taskRequest,
        ).map { it.task }

    suspend fun getTask(workspaceId: String, taskId: String): ApiResponse<Task, ApiError> =
        request<Unit, TaskResponse>(
            method = HttpMethod.Get,
            path = "/workspaces/$workspaceId/tasks/$taskId",
        ).map { it.task }

    suspend fun updateTask(workspaceId: String, taskId: String, taskRequest: TaskRequest): ApiResponse<Task, ApiError> =
        request<TaskRequest, TaskResponse>(
            method = HttpMethod.Put,
            path = "/workspaces/$workspaceId/tasks/$taskId",
            requestBody = taskRequest,
        ).map { it.task }

    suspend fun deleteTask(workspaceId: String, taskId: String): ApiResponse<DeleteTaskResponse, ApiError> =
        request<Unit, DeleteTaskResponse>(
            method = HttpMethod.Delete,
            path = "/workspaces/$workspaceId/tasks/$taskId",
        )

    suspend fun getSubflow(workspaceId: String, subflowId: String): ApiResponse<Subflow, ApiError> =
        request<Unit, SubflowResponse>(
            method = HttpMethod.Get,
            path = "/workspaces/$workspaceId/subflows/$subflowId",
        ).map { it.subflow }

    suspend fun completeFlowAction(
        workspaceId: String,
        flowActionId: String,
        payload: UserResponsePayload
    ): ApiResponse<Unit, ApiError> =
        request<UserResponsePayload, Unit>(
            method = HttpMethod.Post,
            path = "/workspaces/$workspaceId/flow_actions/$flowActionId/complete",
            requestBody = payload
        )

    suspend fun connectToFlowEvents(
        workspaceId: String,
        flowId: String,
        onMessage: suspend (FlowEvent) -> Unit,
        onError: suspend (Throwable) -> Unit = {},
        onClose: suspend (code: Short, reason: String) -> Unit = { _, _ -> },
    ): Result<FlowEventsSession> {
        val session = FlowEventsSession(
            client = client,
            baseUrl = baseUrl,
            workspaceId = workspaceId,
            flowId = flowId,
            dispatcher = dispatcher,
            logger = logger,
        )
        val conn = session.connect(onMessage, onError, onClose)
        return runCatching { conn.await() }.map { session }
    }

    suspend fun connectToFlowActions(
        workspaceId: String,
        flowId: String,
        onMessage: suspend (FlowAction) -> Unit,
        onError: suspend (Throwable) -> Unit = {},
        onClose: suspend (code: Short, reason: String) -> Unit = { _, _ -> },
    ): Result<FlowActionSession> {
        val session = FlowActionSession(
            client = client,
            baseUrl = baseUrl,
            workspaceId = workspaceId,
            flowId = flowId,
            dispatcher = dispatcher,
            logger = logger,
        )
        val conn = session.connect(onMessage, onError, onClose)
        return runCatching { conn.await() }.map { session }
    }

    suspend fun connectToTaskChanges(
        workspaceId: String,
        onMessage: suspend (TaskChangesPayload) -> Unit,
        onError: suspend (Throwable) -> Unit = {},
        onClose: suspend (code: Short, reason: String) -> Unit = { _, _ -> },
    ): Result<TaskChangesSession> {
        val session = TaskChangesSession(
            client = client,
            baseUrl = baseUrl,
            workspaceId = workspaceId,
            dispatcher = dispatcher,
            logger = logger,
        )
        // connect() returns Deferred<Unit>, await() waits for connection setup.
        // runCatching captures connection errors, .map returns the session on success.
        return runCatching { session.connect(onMessage, onError, onClose).await() }.map { session }
    }
}
