package com.cycling.recommendation;

import com.cycling.intervals.api.IntervalsActivity;
import com.cycling.intervals.api.IntervalsApi;
import com.cycling.intervals.api.IntervalsWellnessPoint;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.util.List;

/** Holt Wellness und Aktivitaeten per REST beim intervals-service (Vertrag in {@code intervals-api}). */
public class IntervalsRestClient implements IntervalsData {

    private final RestClient restClient;

    /** @param restClient mit der Basis-URL des intervals-service */
    public IntervalsRestClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public List<IntervalsWellnessPoint> wellness(LocalDate from, LocalDate to) {
        return list(IntervalsApi.WELLNESS, from, to, new ParameterizedTypeReference<>() { });
    }

    @Override
    public List<IntervalsActivity> activities(LocalDate from, LocalDate to) {
        return list(IntervalsApi.ACTIVITIES, from, to, new ParameterizedTypeReference<>() { });
    }

    private <T> List<T> list(String path, LocalDate from, LocalDate to, ParameterizedTypeReference<List<T>> type) {
        try {
            List<T> body = restClient.get()
                    .uri(b -> b.path(path).queryParam("from", from.toString()).queryParam("to", to.toString()).build())
                    .retrieve()
                    .body(type);
            return body == null ? List.of() : body;
        } catch (RestClientException e) {
            throw new IntervalsUnavailableException("intervals-service " + path + ": " + e.getMessage(), e);
        }
    }
}
