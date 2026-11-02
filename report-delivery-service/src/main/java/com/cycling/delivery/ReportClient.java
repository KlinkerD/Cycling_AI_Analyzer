package com.cycling.delivery;

import com.cycling.delivery.report.ReportDto;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.web.client.RestClient;

import java.io.IOException;

/**
 * Holt einen gespeicherten Report beim analysis-service (GET /api/activities/{id}/report).
 * Wirft bei HTTP-Fehler (404 = kein Report) oder unlesbarer Antwort; der Aufrufer zaehlt das als Fehlversuch.
 */
public class ReportClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** @param restClient mit Base-URL des analysis-service */
    public ReportClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public ReportDto fetch(String activityId) {
        String json = restClient.get()
                .uri("/api/activities/{id}/report", activityId)
                .retrieve()
                .body(String.class);
        return parse(json);
    }

    ReportDto parse(String json) {
        try {
            return objectMapper.readValue(json, ReportDto.class);
        } catch (IOException e) {
            throw new IllegalStateException("Report-Antwort nicht lesbar: " + e.getMessage(), e);
        }
    }
}
