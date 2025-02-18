package com.github.sidedev.sidekick.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies.LOWER_CAMEL_CASE
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import com.github.sidedev.sidekick.api.response.ApiResponse
import com.github.sidedev.sidekick.api.response.DeleteTaskResponse
import com.github.sidedev.sidekick.api.response.ErrorResponse

class SidekickService(
    private val baseUrl: String = "http://localhost:8855/api/v1"
) {
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()
    private val objectMapper = ObjectMapper().apply {
        findAndRegisterModules()
        propertyNamingStrategy = LOWER_CAMEL_CASE
    }

    fun getWorkspaces(): ApiResponse<WorkspaceResponse, ErrorResponse> {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI("$baseUrl/workspaces"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            when (response.statusCode()) {
                200 -> ApiResponse.Success(objectMapper.readValue(response.body(), WorkspaceResponse::class.java))
                500 -> ApiResponse.Error(objectMapper.readValue(response.body(), ErrorResponse::class.java))
                else -> ApiResponse.Error(ErrorResponse("Unexpected error: ${response.statusCode()}"))
            }
        } catch (e: Exception) {
            ApiResponse.Error(ErrorResponse(e.message ?: "Unknown error"))
        }
    }

    fun createWorkspace(workspaceRequest: WorkspaceRequest): ApiResponse<WorkspaceResponse.Workspace, ErrorResponse> {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI("$baseUrl/workspaces"))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(workspaceRequest)))
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            when (response.statusCode()) {
                200 -> ApiResponse.Success(objectMapper.readValue(response.body(), WorkspaceResponse.Workspace::class.java))
                400 -> ApiResponse.Error(objectMapper.readValue(response.body(), ErrorResponse::class.java))
                500 -> ApiResponse.Error(objectMapper.readValue(response.body(), ErrorResponse::class.java))
                else -> ApiResponse.Error(ErrorResponse("Unexpected error: ${response.statusCode()}"))
            }
        } catch (e: Exception) {
            ApiResponse.Error(ErrorResponse(e.message ?: "Unknown error"))
        }
    }

    fun getTasks(workspaceId: String, statuses: String? = null): TaskResponse? {
        return try {
            val uriBuilder = StringBuilder("$baseUrl/workspaces/$workspaceId/tasks")
            if (statuses != null) {
                uriBuilder.append("?statuses=$statuses")
            }
            
            val request = HttpRequest.newBuilder()
                .uri(URI(uriBuilder.toString()))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                objectMapper.readValue(response.body(), TaskResponse::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun createTask(workspaceId: String, taskRequest: TaskRequest): Task? {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI("$baseUrl/workspaces/$workspaceId/tasks"))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(taskRequest)))
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                objectMapper.readValue(response.body(), Task::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun getTask(workspaceId: String, taskId: String): Task? {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI("$baseUrl/workspaces/$workspaceId/tasks/$taskId"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                objectMapper.readValue(response.body(), Task::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun updateTask(workspaceId: String, taskId: String, taskRequest: TaskRequest): Task? {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI("$baseUrl/workspaces/$workspaceId/tasks/$taskId"))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(taskRequest)))
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                objectMapper.readValue(response.body(), Task::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun deleteTask(workspaceId: String, taskId: String): ApiResponse<DeleteTaskResponse, ErrorResponse> {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI("$baseUrl/workspaces/$workspaceId/tasks/$taskId"))
                .timeout(Duration.ofSeconds(5))
                .DELETE()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            when (response.statusCode()) {
                200 -> ApiResponse.Success(objectMapper.readValue(response.body(), DeleteTaskResponse::class.java))
                404 -> ApiResponse.Error(objectMapper.readValue(response.body(), ErrorResponse::class.java))
                500 -> ApiResponse.Error(objectMapper.readValue(response.body(), ErrorResponse::class.java))
                else -> ApiResponse.Error(ErrorResponse("Unexpected error: ${response.statusCode()}"))
            }
        } catch (e: Exception) {
            ApiResponse.Error(ErrorResponse(e.message ?: "Unknown error"))
        }
    }
}