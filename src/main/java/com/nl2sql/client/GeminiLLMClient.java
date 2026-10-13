package com.nl2sql.client;

import com.nl2sql.routing.LLMConfiguration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Production implementation of LLMClient communicating with Google Gemini REST API.
 * Employs strict connection/read timeouts and secret protection.
 */
public class GeminiLLMClient implements LLMClient {

    private final LLMConfiguration configuration;
    private final HttpClient httpClient;

    public GeminiLLMClient(LLMConfiguration configuration) {
        this.configuration = configuration;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    @Override
    public String generateContent(String prompt) throws Exception {
        if (configuration == null || !configuration.isConfigured()) {
            throw new IllegalStateException("Gemini API key is not configured in environment variable");
        }

        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" +
                configuration.getModelName() + ":generateContent?key=" + configuration.getApiKey();

        String jsonPayload = buildJsonRequestBody(prompt);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(4))
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Gemini API HTTP Error Status Code: " + response.statusCode());
        }

        return response.body();
    }

    private String buildJsonRequestBody(String prompt) {
        String escapedPrompt = prompt.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
        return "{\"contents\":[{\"parts\":[{\"text\":\"" + escapedPrompt + "\"}]}]}";
    }
}
