package com.cycling.ftp;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;

/** Holt die Streams einer Fahrt beim report-service ({@code GET /api/reports/{activityId}/streams}). */
public class ReportStreamsClient {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final RestClient restClient;

    /** @param restClient mit der Basis-URL des report-service */
    public ReportStreamsClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /** @throws IllegalStateException mit lesbarer Meldung, wenn die Streams nicht abrufbar sind */
    public StreamData streams(String activityId) {
        byte[] body;
        try {
            body = restClient.get()
                    .uri("/api/reports/{activityId}/streams", activityId)
                    .retrieve()
                    .body(byte[].class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                throw new IllegalStateException("Aktivitaet " + activityId + " nicht gefunden", e);
            }
            throw new IllegalStateException("report-service antwortet mit " + e.getStatusCode().value()
                    + " beim Abruf der Streams", e);
        } catch (RestClientException e) {
            throw new IllegalStateException("report-service nicht erreichbar: " + e.getMessage(), e);
        }
        if (body == null || body.length == 0) {
            throw new IllegalStateException("report-service lieferte keine Streams fuer " + activityId);
        }
        try {
            return MAPPER.readValue(body, StreamData.class);
        } catch (IOException e) {
            throw new IllegalStateException("Streams von " + activityId + " nicht lesbar: " + e.getMessage(), e);
        }
    }
}
