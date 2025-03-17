package com.github.sidedev.sidekick.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import com.github.sidedev.sidekick.api.response.ApiResponse
import com.github.sidedev.sidekick.api.response.ApiError
import com.github.sidedev.sidekick.api.response.DeleteTaskResponse

class SidekickService(
    private val baseUrl: String = "http://localhost:8855/api/v1"
) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            })
        }
    }

    private suspend inline fun <reified Req, reified Res : Any> request(
        method: HttpMethod,
        path: String,
        requestBody: Req? = null
    ): ApiResponse<Res, ApiError> {
        return try {
            val response = client.request("$baseUrl$path") {
                this.method = method
                contentType(ContentType.Application.Json)
                if (requestBody != null) {
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
    }

    suspend fun getWorkspaces(): ApiResponse<List<Workspace>, ApiError> {
        return request<Unit, WorkspacesResponse>(
            method = HttpMethod.Get,
            path = "/workspaces"
        ).map { it.workspaces }
    }

    suspend fun createWorkspace(workspaceRequest: WorkspaceRequest): ApiResponse<Workspace, ApiError> {
        return request<WorkspaceRequest, WorkspaceResponse>(
            method = HttpMethod.Post,
            path = "/workspaces",
            requestBody = workspaceRequest
        ).map { it.workspace }
    }

    suspend fun getTasks(workspaceId: String, statuses: String? = null): ApiResponse<List<Task>, ApiError> {
        val path = StringBuilder("/workspaces/$workspaceId/tasks")
        if (statuses != null) {
            path.append("?statuses=$statuses")
        }

        return request<Unit, TasksResponse>(
            method = HttpMethod.Get,
            path = path.toString()
        ).map { it.tasks }
    }

    suspend fun createTask(workspaceId: String, taskRequest: TaskRequest): ApiResponse<Task, ApiError> {
        return request<TaskRequest, TaskResponse>(
            method = HttpMethod.Post,
            path = "/workspaces/$workspaceId/tasks",
            requestBody = taskRequest
        ).map { it.task }
    }

    suspend fun getTask(workspaceId: String, taskId: String): ApiResponse<Task, ApiError> {
        return request<Unit, TaskResponse>(
            method = HttpMethod.Get,
            path = "/workspaces/$workspaceId/tasks/$taskId"
        ).map { it.task }
    }

    suspend fun updateTask(workspaceId: String, taskId: String, taskRequest: TaskRequest): ApiResponse<Task, ApiError> {
        return request<TaskRequest, TaskResponse>(
            method = HttpMethod.Put,
            path = "/workspaces/$workspaceId/tasks/$taskId",
            requestBody = taskRequest
        ).map { it.task }
    }

    suspend fun deleteTask(workspaceId: String, taskId: String): ApiResponse<DeleteTaskResponse, ApiError> {
        return request<Unit, DeleteTaskResponse>(
            method = HttpMethod.Delete,
            path = "/workspaces/$workspaceId/tasks/$taskId"
        )
    }
}