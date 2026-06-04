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

    /**
     * Laedt die Power-Curve (Mean Maximal Power) einer Aktivitaet von intervals.icu
     * als sortierte Map "Dauer in Sekunden -> Watt".
     *
     * Strategie:
     * 1. Primaer: GET /api/v1/activity/{id}/power-curve  (falls verfuegbar; defensiv geparst)
     * 2. Fallback: GET /api/v1/activity/{id}  und Auswertung der diskreten "icu_pm_p_<sec>"-Felder
     *    bzw. eines "icu_pm" Map-Feldes.
     *
     * Liefert eine leere Map, wenn beide Wege scheitern.
     */
    public java.util.SortedMap<Integer, Integer> getActivityPowerCurve(String activityId) {
        validateConfiguration();
        java.util.TreeMap<Integer, Integer> out = new java.util.TreeMap<>();

        // 1. Versuch: dedizierter Endpoint
        try {
            String url = BASE_URL + "/activity/" + activityId + "/power-curve";
            String response = executeGet(url);
            if (response != null && !response.isBlank()) {
                JsonNode root = objectMapper.readTree(response);
                parsePowerCurveJson(root, out);
                if (!out.isEmpty()) {
                    log.info("Power-Curve fuer {} via /power-curve geladen ({} Punkte)", activityId, out.size());
                    return out;
                }
            }
        } catch (Exception e) {
            log.debug("Power-Curve Endpoint fuer {} nicht verfuegbar oder fehlerhaft: {} – Fallback auf Activity-Detail",
                    activityId, e.getMessage());
        }

        // 2. Fallback: Activity-Detail mit icu_pm_p_*
        try {
            String url = BASE_URL + "/activity/" + activityId;
            String response = executeGet(url);
            JsonNode root = objectMapper.readTree(response);
            parseIcuPmFields(root, out);
            log.info("Power-Curve fuer {} via Activity-Detail (icu_pm_p_*) geladen ({} Punkte)", activityId, out.size());
        } catch (Exception e) {
            log.warn("Power-Curve fuer {} konnte nicht geladen werden: {}", activityId, e.getMessage());
        }
        return out;
    }

    /**
     * Versucht, eine Power-Curve aus verschiedenen denkbaren JSON-Formaten zu lesen.
     * Akzeptiert:
     * - Array von { "secs": n, "value"|"watts": w }
     * - Array von { "duration": n, "power"|"watts": w }
     * - Objekt mit "secs":[...], "values"|"watts":[...]
     * - Objekt als Map "secs" -> watts (z.B. "5":510, "60":278, ...)
     */
    private void parsePowerCurveJson(JsonNode root, java.util.TreeMap<Integer, Integer> out) {
        if (root == null || root.isMissingNode() || root.isNull()) return;

        if (root.isArray()) {
            for (JsonNode n : root) {
                Integer secs = firstInt(n, "secs", "duration", "t", "x");
                Integer watts = firstInt(n, "value", "watts", "power", "y");
                if (secs != null && secs > 0 && watts != null && watts >= 0) {
                    out.put(secs, watts);
                }
            }
            return;
        }
        if (root.isObject()) {
            // Variante: parallele Arrays
            JsonNode secsArr = firstNode(root, "secs", "durations", "x");
            JsonNode wattsArr = firstNode(root, "values", "watts", "power", "y");
            if (secsArr != null && secsArr.isArray() && wattsArr != null && wattsArr.isArray()) {
                int n = Math.min(secsArr.size(), wattsArr.size());
                for (int i = 0; i < n; i++) {
                    JsonNode s = secsArr.get(i);
                    JsonNode w = wattsArr.get(i);
                    if (s != null && !s.isNull() && w != null && !w.isNull()) {
                        out.put(s.asInt(), w.asInt());
                    }
                }
                return;
            }
            // Variante: Map "secs" -> watts
            root.fields().forEachRemaining(e -> {
                String key = e.getKey();
                JsonNode v = e.getValue();
                try {
                    int secs = Integer.parseInt(key);
                    if (v.isNumber()) {
                        out.put(secs, v.asInt());
                    }
                } catch (NumberFormatException ignored) {
                    // andere Felder ignorieren
                }
            });
        }
    }

    /**
     * Liest "icu_pm_p_<sec>" Felder aus einem Activity-Detail-Objekt.
     * Beispiel-Felder: icu_pm_p_5, icu_pm_p_60, icu_pm_p_300, icu_pm_p_1200, icu_pm_p_3600 ...
     * Optional gibt es auch ein "icu_pm" Map-Feld – beides wird beruecksichtigt.
     */
    private void parseIcuPmFields(JsonNode activity, java.util.TreeMap<Integer, Integer> out) {
        if (activity == null || !activity.isObject()) return;

        activity.fields().forEachRemaining(e -> {
            String key = e.getKey();
            if (key != null && key.startsWith("icu_pm_p_")) {
                String suffix = key.substring("icu_pm_p_".length());
                try {
                    int secs = Integer.parseInt(suffix);
                    JsonNode v = e.getValue();
                    if (v != null && v.isNumber() && secs > 0) {
                        int w = v.asInt();
                        if (w > 0) out.put(secs, w);
                    }
                } catch (NumberFormatException ignored) { /* skip */ }
            }
        });

        // Zusaetzliches Map-Feld 'icu_pm', falls vorhanden
        JsonNode pm = activity.path("icu_pm");
        if (pm.isObject()) {
            pm.fields().forEachRemaining(e -> {
                try {
                    int secs = Integer.parseInt(e.getKey());
                    JsonNode v = e.getValue();
                    if (v != null && v.isNumber()) {
                        int w = v.asInt();
                        if (w > 0) out.putIfAbsent(secs, w);
                    }
                } catch (NumberFormatException ignored) { /* skip */ }
            });
        }
    }

    private static Integer firstInt(JsonNode obj, String... keys) {
        if (obj == null || !obj.isObject()) return null;
        for (String k : keys) {
            JsonNode v = obj.path(k);
            if (!v.isMissingNode() && !v.isNull() && v.isNumber()) {
                return v.asInt();
            }
        }
        return null;
    }

    private static JsonNode firstNode(JsonNode obj, String... keys) {
        if (obj == null || !obj.isObject()) return null;
        for (String k : keys) {
            JsonNode v = obj.path(k);
            if (!v.isMissingNode() && !v.isNull()) return v;
        }
        return null;
    }
}

