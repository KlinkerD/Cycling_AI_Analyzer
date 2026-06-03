package com.cycling.service;

import com.cycling.dto.ActivityDto;
import com.cycling.dto.AthleteThresholdsDto;
import com.cycling.dto.StreamDataDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class IntervalsService {

    private static final Logger log = LoggerFactory.getLogger(IntervalsService.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public IntervalsService(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Value("${intervals.api.key}")
    private String apiKey;

    @Value("${intervals.athlete.id}")
    private String athleteId;

    private static final String BASE_URL = "https://intervals.icu/api/v1";

    private String basicAuthHeader() {
        // intervals.icu Basic-Auth: username = "API_KEY", password = <api_key>
        String credentials = "API_KEY:" + apiKey;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private void validateConfiguration() {
        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("HIER_")) {
            throw new IllegalStateException("intervals.api.key ist nicht gesetzt. Bitte INTERVALS_API_KEY konfigurieren.");
        }
        if (athleteId == null || athleteId.isBlank() || athleteId.startsWith("HIER_")) {
            throw new IllegalStateException("intervals.athlete.id ist nicht gesetzt. Bitte INTERVALS_ATHLETE_ID konfigurieren.");
        }
    }

    private String executeGet(String url) {
        return restClient.get()
                .uri(url)
                .header("Authorization", basicAuthHeader())
                .retrieve()
                .body(String.class);
    }

    /**
     * Lädt FTP, LTHR und Max-HR des Athleten aus intervals.icu.
     * API: GET /api/v1/athlete/{id}
     */
    public AthleteThresholdsDto getAthleteThresholds() {
        validateConfiguration();
        log.info("Lade Athleten-Schwellenwerte für Athlet {}", athleteId);
        try {
            String url = BASE_URL + "/athlete/" + athleteId;
            String response = executeGet(url);
            JsonNode root = objectMapper.readTree(response);

            String name = root.path("name").asText(null);
            double weight = root.path("icu_weight").asDouble(0);

            // FTP, LTHR und Max-HR aus sportSettings für Ride holen
            Integer ftp = null, lthr = null, maxHr = null;
            JsonNode sportSettings = root.path("sportSettings");
            if (sportSettings.isArray()) {
                for (JsonNode setting : sportSettings) {
                    JsonNode types = setting.path("types");
                    boolean isRide = false;
                    for (JsonNode t : types) {
                        if ("Ride".equals(t.asText()) || "VirtualRide".equals(t.asText())) {
                            isRide = true;
                            break;
                        }
                    }
                    if (isRide) {
                        ftp   = setting.path("ftp").isNull()    ? null : setting.path("ftp").asInt();
                        lthr  = setting.path("lthr").isNull()   ? null : setting.path("lthr").asInt();
                        maxHr = setting.path("max_hr").isNull() ? null : setting.path("max_hr").asInt();
                        break;
                    }
                }
            }

            AthleteThresholdsDto thresholds = new AthleteThresholdsDto(
                    name, ftp, lthr, maxHr, weight > 0 ? weight : null);
            log.info("Schwellenwerte geladen: Name={}, FTP={}, LTHR={}, MaxHR={}, Gewicht={}",
                    thresholds.name(), thresholds.ftp(), thresholds.lthr(), thresholds.maxHr(), thresholds.weight());
            return thresholds;
        } catch (Exception e) {
            log.error("Athleten-Schwellenwerte konnten nicht geladen werden: {}", e.getMessage(), e);
            return new AthleteThresholdsDto(null, null, null, null, null);
        }
    }

    /**
     * Lädt Aktivitäten aus intervals.icu (nur Radfahren / Ride).
     * API: GET /api/v1/athlete/{id}/activities?oldest=...&newest=...
     */
    public List<ActivityDto> getActivities(String from, String to) {
        validateConfiguration();
        log.info("Lade Aktivitäten von {} bis {} für Athlet {}", from, to, athleteId);
        try {
            String url = BASE_URL + "/athlete/" + athleteId + "/activities?oldest=" + from + "&newest=" + to;
            String response = executeGet(url);
            JsonNode root = objectMapper.readTree(response);
            List<ActivityDto> activities = new ArrayList<>();

            if (root.isArray()) {
                for (JsonNode node : root) {
                    ActivityDto dto = objectMapper.treeToValue(node, ActivityDto.class);
                    // Nur Radfahren
                    if ("Ride".equalsIgnoreCase(dto.type()) || "VirtualRide".equalsIgnoreCase(dto.type())) {
                        activities.add(dto);
                    }
                }
            }

            log.info("{} Rad-Aktivitäten gefunden", activities.size());
            return activities;
        } catch (HttpClientErrorException.Forbidden e) {
            log.error("intervals.icu Zugriff verweigert (403) für Athlet {}", athleteId, e);
            throw new RuntimeException("intervals.icu Zugriff verweigert (403). Bitte API-Key und Athlete-ID prüfen.", e);
        } catch (Exception e) {
            log.error("Fehler beim Laden der Aktivitäten: {}", e.getMessage(), e);
            throw new RuntimeException("Fehler beim Laden der intervals.icu Aktivitäten: " + e.getMessage(), e);
        }
    }

    /**
     * Lädt Stream-Daten (Leistung, HF, Geschwindigkeit, Höhe, Distanz) für eine Aktivität.
     * API: GET /api/v1/activity/{id}/streams
     *
     * HINWEIS: Das genaue Format der intervals.icu Streams-API – bitte in der Doku prüfen:
     * https://intervals.icu/api/v1/docs – ggf. muss parseStreams() angepasst werden.
     */
    public StreamDataDto getStreams(String activityId) {
        validateConfiguration();
        log.info("Lade Streams für Aktivität {}", activityId);
        try {
            String url = BASE_URL + "/activity/" + activityId + "/streams";
            String response = executeGet(url);
            return parseStreams(objectMapper.readTree(response));
        } catch (HttpClientErrorException.Forbidden e) {
            log.error("intervals.icu Stream-Zugriff verweigert (403) für Aktivität {}", activityId, e);
            throw new RuntimeException("intervals.icu Zugriff verweigert (403). Bitte API-Key und Athlete-ID prüfen.", e);
        } catch (Exception e) {
            log.error("Fehler beim Laden der Streams: {}", e.getMessage(), e);
            throw new RuntimeException("Fehler beim Laden der Stream-Daten: " + e.getMessage(), e);
        }
    }

    /**
     * Parst die Stream-Antwort von intervals.icu.
     * Format: Array von { "type": "...", "data": [...] }
     */
    private StreamDataDto parseStreams(JsonNode root) {
        Map<String, JsonNode> streamMap = new HashMap<>();

        if (root.isArray()) {
            for (JsonNode streamNode : root) {
                String type = streamNode.path("type").asText();
                JsonNode data = streamNode.path("data");
                streamMap.put(type, data);
            }
        } else if (root.isObject()) {
            // Alternativformat: direkt als Objekt { "watts": [...], "heartrate": [...] }
            root.fields().forEachRemaining(e -> streamMap.put(e.getKey(), e.getValue()));
        }

        return new StreamDataDto(
                parseDoubleList(streamMap.get("distance")),
                parseIntList(streamMap.getOrDefault("watts", streamMap.get("power"))),
                parseIntList(streamMap.get("heartrate")),
                parseDoubleList(streamMap.getOrDefault("velocity_smooth", streamMap.get("velocity"))),
                parseDoubleList(streamMap.get("altitude")),
                parseIntList(streamMap.get("time")),
                parseIntList(streamMap.getOrDefault("cadence", streamMap.get("rpm")))
        );
    }

    private List<Double> parseDoubleList(JsonNode node) {
        if (node == null || !node.isArray()) return Collections.emptyList();
        List<Double> list = new ArrayList<>();
        for (JsonNode n : node) list.add(n.isNull() ? null : n.asDouble());
        return list;
    }

    private List<Integer> parseIntList(JsonNode node) {
        if (node == null || !node.isArray()) return Collections.emptyList();
        List<Integer> list = new ArrayList<>();
        for (JsonNode n : node) list.add(n.isNull() ? null : n.asInt());
        return list;
    }
}

