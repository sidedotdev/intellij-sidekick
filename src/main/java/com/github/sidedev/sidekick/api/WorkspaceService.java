package com.github.sidedev.sidekick.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

public class WorkspaceService {
    private static final String API_BASE_URL = "http://localhost:8855/api/v1";
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public WorkspaceService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public Optional<WorkspaceResponse> getWorkspaces() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE_URL + "/workspaces/"))
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return Optional.of(objectMapper.readValue(response.body(), WorkspaceResponse.class));
            }
            return Optional.empty();
        } catch (IOException | InterruptedException e) {
            return Optional.empty();
        }
    }
}