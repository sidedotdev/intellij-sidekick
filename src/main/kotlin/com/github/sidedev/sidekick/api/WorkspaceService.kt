package com.github.sidedev.sidekick.api

import com.fasterxml.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class WorkspaceService {
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()
    private val objectMapper = ObjectMapper()
    
    fun getWorkspaces(): WorkspaceResponse? {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI("http://localhost:8855/api/v1/workspaces/"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                objectMapper.readValue(response.body(), WorkspaceResponse::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}