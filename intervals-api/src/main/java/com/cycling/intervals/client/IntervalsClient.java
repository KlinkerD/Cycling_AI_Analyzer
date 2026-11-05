package com.cycling.intervals.client;

import com.cycling.intervals.api.IntervalsActivity;
import com.cycling.intervals.api.IntervalsActivityZones;
import com.cycling.intervals.api.IntervalsApi;
import com.cycling.intervals.api.IntervalsAthleteThresholds;
import com.cycling.intervals.api.IntervalsAthleteZones;
import com.cycling.intervals.api.IntervalsPowerCurve;
import com.cycling.intervals.api.IntervalsStreams;
import com.cycling.intervals.api.IntervalsThresholdsUpdate;
import com.cycling.intervals.api.IntervalsWellnessPoint;
import com.cycling.types.ActivityId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * REST-Client fuer den intervals-service, gemeinsam fuer alle Services. Liefert die Vertragstypen aus
 * {@code com.cycling.intervals.api}; die Umwandlung in eigene API-DTOs macht jeder Service selbst.
 * Keine Wiederholungen und kein Cache - beides erledigt der intervals-service.
 *
 * <p>Fehler werfen {@link IntervalsUnavailableException} mit Status und Code aus dem ProblemDetail.
 * Die {@code ...OrEmpty}/{@code ...IfAvailable}-Varianten sind fail-soft fuer Daten, ohne die Report
 * und Analyse auskommen (Schwellen, Zonen, Power-Curve).
 */
public class IntervalsClient {

    private static final Logger log = LoggerFactory.getLogger(IntervalsClient.class);
    private static final ObjectMapper PROBLEM_READER = new ObjectMapper();

    private final RestClient restClient;

    /** @param restClient mit der Basis-URL des intervals-service */
    public IntervalsClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /** Client mit JDK-HttpClient und den angegebenen Timeouts. */
    public static IntervalsClient create(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return new IntervalsClient(RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build());
    }

    /** Rad-Aktivitaeten im Zeitraum. */
    public List<IntervalsActivity> activities(LocalDate from, LocalDate to) {
        return list(IntervalsApi.ACTIVITIES, from, to, new ParameterizedTypeReference<>() { });
    }

    /** Fitness (CTL), Fatigue (ATL), Form und Ramp pro Tag, aufsteigend. */
    public List<IntervalsWellnessPoint> wellness(LocalDate from, LocalDate to) {
        return list(IntervalsApi.WELLNESS, from, to, new ParameterizedTypeReference<>() { });
    }

    public IntervalsStreams streams(ActivityId activityId) {
        return get(IntervalsApi.ACTIVITY_STREAMS, IntervalsStreams.class, activityId);
    }

    public IntervalsAthleteThresholds athleteThresholds() {
        return get(IntervalsApi.ATHLETE_THRESHOLDS, IntervalsAthleteThresholds.class);
    }

    public IntervalsAthleteZones athleteZones() {
        return get(IntervalsApi.ATHLETE_ZONES, IntervalsAthleteZones.class);
    }

    public IntervalsActivityZones activityZones(ActivityId activityId) {
        return get(IntervalsApi.ACTIVITY_ZONES, IntervalsActivityZones.class, activityId);
    }

    public IntervalsPowerCurve powerCurve(ActivityId activityId) {
        return get(IntervalsApi.ACTIVITY_POWER_CURVE, IntervalsPowerCurve.class, activityId);
    }

    /**
     * Schreibt neue Schwellenwerte nach intervals.icu (null-Felder bleiben unverändert) und liefert
     * die frischen Werte. Unplausible Werte kommen als {@link IntervalsUnavailableException} mit 400.
     */
    public IntervalsAthleteThresholds updateThresholds(IntervalsThresholdsUpdate update) {
        IntervalsAthleteThresholds body = call(() -> restClient.put()
                .uri(IntervalsApi.ATHLETE_THRESHOLDS)
                .contentType(MediaType.APPLICATION_JSON)
                .body(update)
                .retrieve()
                .onStatus(HttpStatusCode::isError, IntervalsClient::throwProblem)
                .body(IntervalsAthleteThresholds.class));
        if (body == null) {
            throw new IntervalsUnavailableException(HttpStatus.BAD_GATEWAY, "EMPTY_RESPONSE",
                    "intervals-service lieferte nach dem Speichern keine Daten", null, null);
        }
        return body;
    }

    /** Leert die Caches des intervals-service. */
    public void evictCaches() {
        call(() -> restClient.post()
                .uri(IntervalsApi.CACHE_EVICT)
                .retrieve()
                .onStatus(HttpStatusCode::isError, IntervalsClient::throwProblem)
                .toBodilessEntity());
    }

    // ---- fail-soft --------------------------------------------------------------------------

    /** Schwellenwerte; bei Fehlern alle Felder null. */
    public IntervalsAthleteThresholds athleteThresholdsOrEmpty() {
        try {
            return athleteThresholds();
        } catch (IntervalsUnavailableException e) {
            log.error("Athleten-Schwellenwerte nicht ladbar: {}", e.getMessage());
            return emptyThresholds();
        }
    }

    /** Athleten-Zonen; bei Fehlern leere Schwellen und keine Zonen (HF-Referenz "lthr"). */
    public IntervalsAthleteZones athleteZonesOrEmpty() {
        try {
            IntervalsAthleteZones z = athleteZones();
            return new IntervalsAthleteZones(z.thresholds() != null ? z.thresholds() : emptyThresholds(),
                    z.powerZones() != null ? z.powerZones() : List.of(),
                    z.hrZones() != null ? z.hrZones() : List.of(),
                    z.hrReference() != null ? z.hrReference() : "lthr");
        } catch (IntervalsUnavailableException e) {
            log.error("Athleten-Zonen nicht ladbar: {}", e.getMessage());
            return new IntervalsAthleteZones(emptyThresholds(), List.of(), List.of(), "lthr");
        }
    }

    /** Zonen einer Aktivitaet; leer, wenn der Abruf scheitert (Indoor-Kennung und FTP dann unbekannt). */
    public Optional<IntervalsActivityZones> activityZonesIfAvailable(ActivityId activityId) {
        try {
            return Optional.of(activityZones(activityId));
        } catch (IntervalsUnavailableException e) {
            log.error("Aktivitaets-Zonen fuer {} nicht ladbar: {}", activityId, e.getMessage());
            return Optional.empty();
        }
    }

    /** Power-Curve; ohne Punkte, wenn der Abruf scheitert. */
    public IntervalsPowerCurve powerCurveOrEmpty(ActivityId activityId) {
        try {
            IntervalsPowerCurve curve = powerCurve(activityId);
            return curve.points() != null ? curve : new IntervalsPowerCurve(List.of());
        } catch (IntervalsUnavailableException e) {
            log.warn("Power-Curve fuer {} nicht ladbar: {}", activityId, e.getMessage());
            return new IntervalsPowerCurve(List.of());
        }
    }

    /** Leert die Caches, Fehler werden nur geloggt (Refresh-Button). */
    public void evictCachesQuietly() {
        try {
            evictCaches();
        } catch (IntervalsUnavailableException e) {
            log.warn("Caches des intervals-service nicht geleert: {}", e.getMessage());
        }
    }

    public static IntervalsAthleteThresholds emptyThresholds() {
        return new IntervalsAthleteThresholds(null, null, null, null, null, null);
    }

    // ---- Helfer ------------------------------------------------------------------------------

    private <T> List<T> list(String path, LocalDate from, LocalDate to, ParameterizedTypeReference<List<T>> type) {
        List<T> body = call(() -> restClient.get()
                .uri(b -> b.path(path).queryParam("from", from.toString()).queryParam("to", to.toString()).build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, IntervalsClient::throwProblem)
                .body(type));
        return body == null ? List.of() : body;
    }

    private <T> T get(String path, Class<T> type, Object... uriVariables) {
        T body = call(() -> restClient.get()
                .uri(path, uriVariables)
                .retrieve()
                .onStatus(HttpStatusCode::isError, IntervalsClient::throwProblem)
                .body(type));
        if (body == null) {
            throw new IntervalsUnavailableException(HttpStatus.BAD_GATEWAY, "EMPTY_RESPONSE",
                    "intervals-service lieferte keine Daten fuer " + path, null, null);
        }
        return body;
    }

    private static <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (ResourceAccessException e) {
            throw new IntervalsUnavailableException("intervals-service nicht erreichbar: " + e.getMessage(), e);
        }
    }

    /** Uebersetzt die ProblemDetail-Antwort des intervals-service. */
    private static void throwProblem(org.springframework.http.HttpRequest request, ClientHttpResponse response)
            throws IOException {
        int status = response.getStatusCode().value();
        String code = "INTERVALS_UNAVAILABLE";
        String detail = "intervals-service antwortet mit " + status;
        try {
            JsonNode problem = PROBLEM_READER.readTree(new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8));
            JsonNode codeNode = problem.path(IntervalsApi.PROBLEM_CODE);
            if (codeNode.isMissingNode()) codeNode = problem.path("properties").path(IntervalsApi.PROBLEM_CODE);
            if (codeNode.isTextual()) code = codeNode.asText();
            if (problem.path("detail").isTextual()) detail = problem.path("detail").asText();
        } catch (IOException | RuntimeException ignored) {
            // kein ProblemDetail: Standardtext behalten
        }
        HttpStatus mapped = status == 400 ? HttpStatus.BAD_REQUEST
                : status == 404 ? HttpStatus.NOT_FOUND
                : status == 503 ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.BAD_GATEWAY;
        throw new IntervalsUnavailableException(mapped, code, detail, retryAfter(response.getHeaders()), null);
    }

    private static Long retryAfter(HttpHeaders headers) {
        String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null) return null;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
