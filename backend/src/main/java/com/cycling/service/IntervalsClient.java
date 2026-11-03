package com.cycling.service;

import com.cycling.dto.ActivityDto;
import com.cycling.dto.ActivityZonesDto;
import com.cycling.dto.AthleteThresholdsDto;
import com.cycling.dto.AthleteZonesDto;
import com.cycling.dto.StreamDataDto;
import com.cycling.dto.WellnessPointDto;
import com.cycling.dto.ZoneDistributionDto;
import com.cycling.dto.ZoneDto;
import com.cycling.intervals.api.IntervalsActivity;
import com.cycling.intervals.api.IntervalsActivityZones;
import com.cycling.intervals.api.IntervalsApi;
import com.cycling.intervals.api.IntervalsAthleteThresholds;
import com.cycling.intervals.api.IntervalsAthleteZones;
import com.cycling.intervals.api.IntervalsPowerCurve;
import com.cycling.intervals.api.IntervalsStreams;
import com.cycling.intervals.api.IntervalsWellnessPoint;
import com.cycling.intervals.api.IntervalsZone;
import com.cycling.intervals.api.IntervalsZoneShare;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * Holt die intervals.icu-Daten beim intervals-service (REST, Vertrag in {@code intervals-api}) und wandelt
 * sie in die DTOs der Backend-API um. Keine eigenen Wiederholungen und kein Cache - beides erledigt der
 * intervals-service.
 *
 * <p>Wie frueher fail-soft: Schwellenwerte, Zonen und Power-Curve liefern bei Fehlern leere Werte (Report
 * und Analyse kommen ohne aus). Aktivitaeten, Streams und Wellness werfen {@link IntervalsUnavailableException}.
 */
public class IntervalsClient {

    private static final Logger log = LoggerFactory.getLogger(IntervalsClient.class);
    private static final ObjectMapper PROBLEM_READER = new ObjectMapper();

    private final RestClient restClient;

    /** @param restClient mit der Basis-URL des intervals-service */
    public IntervalsClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /** Rad-Aktivitaeten im Zeitraum (yyyy-MM-dd). */
    public List<ActivityDto> getActivities(String from, String to) {
        List<IntervalsActivity> activities = call(() -> restClient.get()
                .uri(b -> b.path(IntervalsApi.ACTIVITIES).queryParam("from", from).queryParam("to", to).build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, IntervalsClient::throwProblem)
                .body(new ParameterizedTypeReference<List<IntervalsActivity>>() { }));
        return activities == null ? List.of() : activities.stream().map(ActivityDto::from).toList();
    }

    public StreamDataDto getStreams(String activityId) {
        IntervalsStreams s = get(IntervalsApi.ACTIVITY_STREAMS, IntervalsStreams.class, activityId);
        return new StreamDataDto(list(s.distance()), list(s.watts()), list(s.heartrate()), list(s.velocity()),
                list(s.altitude()), list(s.time()), list(s.cadence()));
    }

    /** Fitness (CTL), Fatigue (ATL) und Form pro Tag, aufsteigend. */
    public List<WellnessPointDto> getWellness(String from, String to) {
        List<IntervalsWellnessPoint> points = call(() -> restClient.get()
                .uri(b -> b.path(IntervalsApi.WELLNESS).queryParam("from", from).queryParam("to", to).build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, IntervalsClient::throwProblem)
                .body(new ParameterizedTypeReference<List<IntervalsWellnessPoint>>() { }));
        return points == null ? List.of() : points.stream()
                .map(p -> new WellnessPointDto(p.date(), p.ctl(), p.atl(), p.form()))
                .toList();
    }

    /** Fail-soft: leere Schwellenwerte, wenn der Abruf scheitert. */
    public AthleteThresholdsDto getAthleteThresholds() {
        try {
            return toThresholds(get(IntervalsApi.ATHLETE_THRESHOLDS, IntervalsAthleteThresholds.class));
        } catch (IntervalsUnavailableException e) {
            log.error("Athleten-Schwellenwerte nicht ladbar: {}", e.getMessage());
            return emptyThresholds();
        }
    }

    /** Fail-soft: keine Zonen, wenn der Abruf scheitert. */
    public AthleteZonesDto getAthleteZones() {
        try {
            IntervalsAthleteZones z = get(IntervalsApi.ATHLETE_ZONES, IntervalsAthleteZones.class);
            return new AthleteZonesDto(toThresholds(z.thresholds()), zones(z.powerZones()), zones(z.hrZones()),
                    z.hrReference() != null ? z.hrReference() : "lthr");
        } catch (IntervalsUnavailableException e) {
            log.error("Athleten-Zonen nicht ladbar: {}", e.getMessage());
            return new AthleteZonesDto(emptyThresholds(), List.of(), List.of(), "lthr");
        }
    }

    /** Fail-soft: leere Zonen und unbekannte Indoor-Kennung/FTP, wenn der Abruf scheitert. */
    public ActivityZonesDto getActivityZones(String activityId) {
        try {
            IntervalsActivityZones z = get(IntervalsApi.ACTIVITY_ZONES, IntervalsActivityZones.class, activityId);
            return new ActivityZonesDto(shares(z.powerZones()), shares(z.hrZones()), z.indoor(), z.icuFtp());
        } catch (IntervalsUnavailableException e) {
            log.error("Aktivitaets-Zonen fuer {} nicht ladbar: {}", activityId, e.getMessage());
            return new ActivityZonesDto(List.of(), List.of(), null, null);
        }
    }

    /** Power-Curve als "Dauer in s -> Watt"; fail-soft leer. */
    public SortedMap<Integer, Integer> getActivityPowerCurve(String activityId) {
        TreeMap<Integer, Integer> out = new TreeMap<>();
        try {
            IntervalsPowerCurve curve = get(IntervalsApi.ACTIVITY_POWER_CURVE, IntervalsPowerCurve.class, activityId);
            if (curve.points() != null) {
                curve.points().forEach(p -> out.put(p.seconds(), p.watts()));
            }
        } catch (IntervalsUnavailableException e) {
            log.warn("Power-Curve fuer {} nicht ladbar: {}", activityId, e.getMessage());
        }
        return out;
    }

    /** Leert die Caches des intervals-service (Refresh-Button im Frontend); fail-soft. */
    public void evictCaches() {
        try {
            call(() -> restClient.post()
                    .uri(IntervalsApi.CACHE_EVICT)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, IntervalsClient::throwProblem)
                    .toBodilessEntity());
        } catch (IntervalsUnavailableException e) {
            log.warn("Caches des intervals-service nicht geleert: {}", e.getMessage());
        }
    }

    // ---- Helfer ------------------------------------------------------------------------------

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
            throw new IntervalsUnavailableException(HttpStatus.SERVICE_UNAVAILABLE,
                    IntervalsUnavailableException.SERVICE_UNREACHABLE,
                    "intervals-service nicht erreichbar: " + e.getMessage(), null, e);
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
        HttpStatus mapped = status == 404 ? HttpStatus.NOT_FOUND
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

    private static AthleteThresholdsDto toThresholds(IntervalsAthleteThresholds t) {
        if (t == null) return emptyThresholds();
        return new AthleteThresholdsDto(t.name(), t.ftp(), t.indoorFtp(), t.lthr(), t.maxHr(), t.weight());
    }

    private static AthleteThresholdsDto emptyThresholds() {
        return new AthleteThresholdsDto(null, null, null, null, null, null);
    }

    private static List<ZoneDto> zones(List<IntervalsZone> zones) {
        if (zones == null) return List.of();
        return zones.stream()
                .map(z -> new ZoneDto(z.index(), z.name(), z.lowerPct(), z.upperPct(), z.lowerValue(),
                        z.upperValue(), z.color()))
                .toList();
    }

    private static List<ZoneDistributionDto> shares(List<IntervalsZoneShare> shares) {
        if (shares == null) return List.of();
        return shares.stream()
                .map(s -> new ZoneDistributionDto(s.zoneName(), s.zoneLabel(), s.seconds(), s.percentage(), s.color()))
                .toList();
    }

    private static <T> List<T> list(List<T> values) {
        return values != null ? values : Collections.emptyList();
    }
}
