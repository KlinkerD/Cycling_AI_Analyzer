package com.cycling.service;

import com.cycling.dto.ActivityDto;
import com.cycling.dto.AthleteThresholdsDto;
import com.cycling.dto.AthleteZonesDto;
import com.cycling.dto.StreamDataDto;
import com.cycling.dto.WellnessPointDto;
import com.cycling.dto.ZoneDto;
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

    // ====================================================================================
    // Overview-Page: Wellness (CTL/ATL/Form) und Athleten-Zonen
    // ====================================================================================

    /**
     * Coggan-Farbschema fuer 7 Zonen. Falls mehr Zonen vorhanden sind, wird die letzte Farbe
     * dupliziert.
     */
    private static final String[] ZONE_COLORS = {
            "#9e9e9e", // Z1 grau
            "#42a5f5", // Z2 blau
            "#66bb6a", // Z3 gruen
            "#ffca28", // Z4 gelb
            "#ff9800", // Z5 orange
            "#ef5350", // Z6 rot
            "#b71c1c"  // Z7 dunkelrot
    };

    /**
     * Lädt die Wellness-Daten (CTL, ATL) für einen Zeitraum aus intervals.icu.
     * API: GET /api/v1/athlete/{id}/wellness?oldest=YYYY-MM-DD&newest=YYYY-MM-DD
     * <p>
     * Form (TSB) wird lokal als CTL - ATL berechnet.
     */
    public List<WellnessPointDto> getWellness(String from, String to) {
        validateConfiguration();
        log.info("Lade Wellness-Daten von {} bis {} fuer Athlet {}", from, to, athleteId);
        try {
            String url = BASE_URL + "/athlete/" + athleteId + "/wellness?oldest=" + from + "&newest=" + to;
            String response = executeGet(url);
            JsonNode root = objectMapper.readTree(response);
            List<WellnessPointDto> result = new ArrayList<>();
            if (root.isArray()) {
                for (JsonNode n : root) {
                    String date = n.path("id").asText(null);
                    if (date == null || date.isBlank()) {
                        date = n.path("date").asText(null);
                    }
                    Double ctl = n.path("ctl").isNumber() ? n.path("ctl").asDouble() : null;
                    Double atl = n.path("atl").isNumber() ? n.path("atl").asDouble() : null;
                    Double form = (ctl != null && atl != null) ? ctl - atl : null;
                    if (date != null) {
                        result.add(new WellnessPointDto(date, ctl, atl, form));
                    }
                }
            }
            // Aufsteigend nach Datum sortieren (intervals.icu liefert i.d.R. neueste-zuerst)
            result.sort(Comparator.comparing(WellnessPointDto::date));
            log.info("{} Wellness-Punkte geladen", result.size());
            return result;
        } catch (Exception e) {
            log.error("Fehler beim Laden der Wellness-Daten: {}", e.getMessage(), e);
            throw new RuntimeException("Fehler beim Laden der Wellness-Daten: " + e.getMessage(), e);
        }
    }

    /**
     * Lädt FTP, LTHR, MaxHR sowie die Power- und HF-Zonen des Athleten aus intervals.icu.
     * Wird ueber {@code sportSettings[type=Ride]} gelesen.
     */
    public AthleteZonesDto getAthleteZones() {
        validateConfiguration();
        log.info("Lade Athleten-Zonen fuer Athlet {}", athleteId);
        try {
            String url = BASE_URL + "/athlete/" + athleteId;
            String response = executeGet(url);
            JsonNode root = objectMapper.readTree(response);

            String name = root.path("name").asText(null);
            double weight = root.path("icu_weight").asDouble(0);

            Integer ftp = null, lthr = null, maxHr = null;
            List<Double> powerPercents = Collections.emptyList();
            List<String> powerNames = Collections.emptyList();
            List<Double> hrPercents = Collections.emptyList();
            List<String> hrNames = Collections.emptyList();
            boolean hrZonesAreMaxHr = false;

            JsonNode sportSettings = root.path("sportSettings");
            if (sportSettings.isArray()) {
                for (JsonNode setting : sportSettings) {
                    boolean isRide = false;
                    for (JsonNode t : setting.path("types")) {
                        if ("Ride".equals(t.asText()) || "VirtualRide".equals(t.asText())) {
                            isRide = true;
                            break;
                        }
                    }
                    if (!isRide) continue;

                    ftp = setting.path("ftp").isNull() ? null : setting.path("ftp").asInt();
                    lthr = setting.path("lthr").isNull() ? null : setting.path("lthr").asInt();
                    maxHr = setting.path("max_hr").isNull() ? null : setting.path("max_hr").asInt();

                    powerPercents = parseDoubleList(setting.path("power_zones"));
                    powerNames = parseStringList(setting.path("power_zone_names"));
                    hrPercents = parseDoubleList(setting.path("hr_zones"));
                    hrNames = parseStringList(setting.path("hr_zone_names"));
                    hrZonesAreMaxHr = setting.path("hr_zones_max_hr").asBoolean(false);
                    break;
                }
            }

            AthleteThresholdsDto thresholds = new AthleteThresholdsDto(
                    name, ftp, lthr, maxHr, weight > 0 ? weight : null);

            // Power-Zonen: intervals.icu liefert Prozente als Dezimalzahlen (0.55, 0.75, ...)
            List<ZoneDto> powerZones = ftp != null && ftp > 0
                    ? buildPowerZones(powerPercents, powerNames, ftp)
                    : Collections.emptyList();

            log.debug("Power-Zonen-Rohdaten: percentages={}, names={}", powerPercents, powerNames);

            // HF-Zonen: intervals.icu liefert ABSOLUTE BPM-Werte (140, 155, 162, ...)
            int hrReferenceVal;
            String hrReference;
            if (hrZonesAreMaxHr) {
                hrReference = "max_hr";
                hrReferenceVal = maxHr != null ? maxHr : 0;
            } else {
                hrReference = "lthr";
                hrReferenceVal = lthr != null ? lthr : 0;
            }
            List<ZoneDto> hrZones = hrReferenceVal > 0
                    ? buildHrZones(hrPercents, hrNames, hrReferenceVal)
                    : Collections.emptyList();

            log.info("Zonen geladen: FTP={}, LTHR={}, MaxHR={}, PowerZonen={}, HFZonen={} (Ref={})",
                    ftp, lthr, maxHr, powerZones.size(), hrZones.size(), hrReference);

            return new AthleteZonesDto(thresholds, powerZones, hrZones, hrReference);
        } catch (Exception e) {
            log.error("Athleten-Zonen konnten nicht geladen werden: {}", e.getMessage(), e);
            return new AthleteZonesDto(
                    new AthleteThresholdsDto(null, null, null, null, null),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    "lthr"
            );
        }
    }

    /**
     * Baut Power-Zonen aus intervals.icu Prozent-Schwellen.
     * Format: percentages = [55, 75, 90, 105, 120, 150, ...] (Ganzzahlen = Prozent, Obere Grenze je Zone)
     * Namen: ["Active Recovery", "Endurance", "Tempo", "Threshold", "VO2 Max", "Anaerobic", "Neuromuscular", "Sweet Spot"]
     *
     * Die letzte reguläre Zone (Z7) ist open-ended. Sweet Spot (SS) wird separat behandelt.
     */
    private List<ZoneDto> buildPowerZones(List<Double> percentages, List<String> names, int ftp) {
        if (percentages == null) percentages = Collections.emptyList();
        if (names == null) names = Collections.emptyList();
        if (percentages.isEmpty() && names.isEmpty()) return Collections.emptyList();

        List<ZoneDto> zones = new ArrayList<>();
        int regularZones = Math.min(7, Math.max(names.size(), percentages.size()));

        // Z1-Z7 (reguläre Zonen)
        for (int i = 0; i < regularZones; i++) {
            // intervals.icu liefert Ganzzahlen (55, 75, 90, ...) → durch 100 teilen
            double lowerPct = (i == 0) ? 0.0 : safeGet(percentages, i - 1, 0.0) / 100.0;
            Double upperPct = i < percentages.size() ? percentages.get(i) / 100.0 : null;
            // Z7 ist open-ended
            if (i == regularZones - 1 && regularZones == 7) upperPct = null;

            Integer lowerVal = (int) Math.round(lowerPct * ftp);
            Integer upperVal = upperPct == null ? null : (int) Math.round(upperPct * ftp);

            String name = i < names.size() ? names.get(i) : "Z" + (i + 1);
            String color = ZONE_COLORS[Math.min(i, ZONE_COLORS.length - 1)];

            zones.add(new ZoneDto(i + 1, name, lowerPct, upperPct, lowerVal, upperVal, color));
        }

        // Sweet Spot (SS) IMMER hinzufügen (Coggan-Standard: 84% - 97% FTP)
        // intervals.icu zeigt SS in der UI, liefert es aber evtl. nicht über power_zones API.
        // Daher fügen wir es immer manuell hinzu.
        boolean hasSweetSpot = false;
        for (int i = regularZones; i < names.size(); i++) {
            String name = names.get(i);
            if (name != null && (name.equalsIgnoreCase("Sweet Spot")
                              || name.equalsIgnoreCase("SweetSpot")
                              || name.equalsIgnoreCase("SS"))) {
                hasSweetSpot = true;
                break;
            }
        }

        if (!hasSweetSpot) {
            // Sweet Spot nicht in intervals.icu-Daten gefunden → Coggan-Standard hinzufügen
            double lowerPct = 0.84;
            double upperPct = 0.97;
            Integer lowerVal = (int) Math.round(lowerPct * ftp);
            Integer upperVal = (int) Math.round(upperPct * ftp);
            zones.add(new ZoneDto(8, "Sweet Spot", lowerPct, upperPct, lowerVal, upperVal, "#66bb6a")); // grün
            log.debug("Sweet Spot als Standard-Zone (84-97% FTP) hinzugefügt");
        }

        log.debug("Power-Zonen gebaut: {} Zonen (inkl. SS)", zones.size());
        return zones;
    }

    /**
     * Baut HF-Zonen aus intervals.icu ABSOLUTEN BPM-Werten.
     * Format: percentages = [140, 155, 162, 173, 178, 183, ...] (BPM, NICHT Prozente!)
     * Die Prozente werden rückgerechnet als bpm/referenceHr.
     */
    private List<ZoneDto> buildHrZones(List<Double> bpmLimits, List<String> names, int referenceHr) {
        if (bpmLimits == null) bpmLimits = Collections.emptyList();
        if (names == null) names = Collections.emptyList();
        int n = Math.max(names.size(), bpmLimits.size());
        if (n == 0) return Collections.emptyList();

        List<ZoneDto> zones = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            // Untere Grenze: vorheriger Wert + 1, oder 0 bei Z1
            int lowerVal = (i == 0) ? 0 : ((int) Math.round(safeGet(bpmLimits, i - 1, 0.0))) + 1;
            Integer upperVal = i < bpmLimits.size() ? (int) Math.round(bpmLimits.get(i)) : null;
            // Letzte Zone ist open-ended
            if (i == n - 1) upperVal = null;

            double lowerPct = (double) lowerVal / referenceHr;
            Double upperPct = upperVal != null ? (double) upperVal / referenceHr : null;

            String name = i < names.size() ? names.get(i) : "Z" + (i + 1);
            String color = ZONE_COLORS[Math.min(i, ZONE_COLORS.length - 1)];

            zones.add(new ZoneDto(i + 1, name, lowerPct, upperPct, lowerVal, upperVal, color));
        }
        return zones;
    }

    private static double safeGet(List<Double> list, int idx, double def) {
        return (idx >= 0 && idx < list.size() && list.get(idx) != null) ? list.get(idx) : def;
    }

    private List<String> parseStringList(JsonNode node) {
        if (node == null || !node.isArray()) return Collections.emptyList();
        List<String> list = new ArrayList<>();
        for (JsonNode n : node) list.add(n.isNull() ? null : n.asText());
        return list;
    }
}

