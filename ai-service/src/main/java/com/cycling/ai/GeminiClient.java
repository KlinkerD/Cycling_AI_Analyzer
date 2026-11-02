package com.cycling.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Ruft die Gemini-API auf und liefert den Antworttext des ersten Kandidaten. */
public class GeminiClient {

    private static final String URL = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public GeminiClient(RestClient restClient, ObjectMapper objectMapper, String apiKey, String model) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
    }

    /**
     * @param json true = Gemini soll reines JSON liefern (responseMimeType application/json)
     * @throws GeminiException bei HTTP-Fehler oder leerer/unlesbarer Antwort
     */
    public String generate(String prompt, double temperature, boolean json) {
        Map<String, Object> generationConfig = json
                ? Map.of("responseMimeType", "application/json", "temperature", temperature)
                : Map.of("temperature", temperature);
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", generationConfig);

        String response;
        try {
            response = restClient.post()
                    .uri(URL, model)
                    // Key als Header statt Query-Parameter, damit er nicht in URL-Logs landet.
                    .header("x-goog-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RuntimeException e) {
            throw new GeminiException("Gemini-Aufruf fehlgeschlagen: " + e.getMessage(), e);
        }
        return firstCandidateText(response);
    }

    private String firstCandidateText(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            String text = root.path("candidates").path(0)
                    .path("content").path("parts").path(0)
                    .path("text").asText("").strip();
            if (text.isBlank()) {
                throw new GeminiException("Gemini lieferte eine leere Antwort", null);
            }
            return text;
        } catch (IOException e) {
            throw new GeminiException("Gemini-Antwort nicht lesbar: " + e.getMessage(), e);
        }
    }

    public static class GeminiException extends RuntimeException {
        public GeminiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
