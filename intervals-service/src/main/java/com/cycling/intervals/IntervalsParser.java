package com.cycling.intervals;

import com.cycling.intervals.api.IntervalsActivity;
import com.cycling.intervals.api.IntervalsActivityZones;
import com.cycling.intervals.api.IntervalsAthleteThresholds;
import com.cycling.intervals.api.IntervalsAthleteZones;
import com.cycling.intervals.api.IntervalsPowerCurve;
import com.cycling.intervals.api.IntervalsStreams;
import com.cycling.intervals.api.IntervalsWellnessPoint;
import com.cycling.intervals.api.IntervalsZone;
import com.cycling.intervals.api.IntervalsZoneShare;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Liest die Rohantworten von intervals.icu (API-Doku: https://intervals.icu/api/v1/docs) und baut die
 * Records aus {@code intervals-api}. Nur hier stecken die Feldnamen und Eigenheiten von intervals.icu.
 */
public final class IntervalsParser {

    /** Coggan-Farben fuer 7 Zonen; weitere Zonen bekommen die letzte Farbe. */
    private static final String[] ZONE_COLORS = {
            "#9e9e9e", // Z1 grau
            "#42a5f5", // Z2 blau
            "#66bb6a", // Z3 gruen
            "#ffca28", // Z4 gelb
            "#ff9800", // Z5 orange
            "#ef5350", // Z6 rot
            "#b71c1c"  // Z7 dunkelrot
    };

    private IntervalsParser() {
    }

    // ---- Aktivitaeten ------------------------------------------------------------------------

    /** Nur Rad-Aktivitaeten (Ride, VirtualRide) aus {@code GET /athlete/{id}/activities}. */
    public static List<IntervalsActivity> rideActivities(JsonNode root) {
        List<IntervalsActivity> out = new ArrayList<>();
        if (root == null || !root.isArray()) return out;
        for (JsonNode node : root) {
            String type = text(node, "type");
            if ("Ride".equalsIgnoreCase(type) || "VirtualRide".equalsIgnoreCase(type)) {
                out.add(activity(node));
            }
        }
        return out;
    }

    /**
     * Eine Aktivitaet. intervals.icu liefert neben den Originalfeldern (z. B. von Strava: "average_watts")
     * eigene Werte ("icu_average_watts"), die auch bei Garmin-Direkt-Uploads gesetzt sind - diese gewinnen.
     */
    public static IntervalsActivity activity(JsonNode n) {
        Double icuWeighted = dbl(n, "icu_weighted_avg_watts");
        return new IntervalsActivity(
                text(n, "id"),
                text(n, "name"),
                text(n, "start_date_local"),
                text(n, "type"),
                dbl(n, "distance"),
                integer(n, "moving_time"),
                integer(n, "elapsed_time"),
                firstNonNull(dbl(n, "icu_average_watts"), icuWeighted, dbl(n, "average_watts")),
                icuWeighted,
                firstNonNull(dbl(n, "icu_max_watts"), dbl(n, "max_watts")),
                firstNonNull(dbl(n, "icu_average_heartrate"), dbl(n, "average_heartrate")),
                firstNonNull(dbl(n, "icu_max_heartrate"), dbl(n, "max_heartrate")),
                firstNonNull(dbl(n, "icu_average_speed"), dbl(n, "average_speed")),
                firstNonNull(dbl(n, "icu_elevation_gain"), dbl(n, "total_elevation_gain")),
                dbl(n, "icu_training_load"),
                percentToFactor(dbl(n, "icu_intensity")));
    }

    /** intervals.icu liefert die Intensitaet in Prozent (78.4), der Vertrag als Faktor (0.784). */
    private static Double percentToFactor(Double percent) {
        return percent == null ? null : percent / 100.0;
    }

    // ---- Streams -----------------------------------------------------------------------------

    /**
     * {@code GET /activity/{id}/streams}: Array von {"type": "...", "data": [...]}; alternativ ein Objekt
     * {"watts": [...], ...}. GPS kommt als {"type": "latlng", "data": [lat...], "data2": [lng...]}.
     */
    public static IntervalsStreams streams(JsonNode root) {
        Map<String, JsonNode> streams = new HashMap<>();
        JsonNode lng = null;
        if (root != null && root.isArray()) {
            for (JsonNode stream : root) {
                String type = stream.path("type").asText();
                streams.put(type, stream.path("data"));
                if ("latlng".equals(type)) lng = stream.path("data2");
            }
        } else if (root != null && root.isObject()) {
            root.fields().forEachRemaining(e -> streams.put(e.getKey(), e.getValue()));
        }
        return new IntervalsStreams(
                doubles(streams.get("distance")),
                ints(streams.getOrDefault("watts", streams.get("power"))),
                ints(streams.get("heartrate")),
                doubles(streams.getOrDefault("velocity_smooth", streams.get("velocity"))),
                doubles(streams.get("altitude")),
                ints(streams.get("time")),
                ints(streams.getOrDefault("cadence", streams.get("rpm"))),
                doubles(streams.get("latlng")),
                doubles(lng));
    }

    // ---- Wellness ----------------------------------------------------------------------------

    /**
     * {@code GET /athlete/{id}/wellness}: ein Eintrag pro Tag (Datum in "id"). Form = CTL - ATL.
     * Ergebnis aufsteigend nach Datum (intervals.icu liefert meist neueste zuerst).
     */
    public static List<IntervalsWellnessPoint> wellness(JsonNode root) {
        List<IntervalsWellnessPoint> out = new ArrayList<>();
        if (root == null || !root.isArray()) return out;
        for (JsonNode n : root) {
            String date = text(n, "id");
            if (date == null || date.isBlank()) date = text(n, "date");
            if (date == null || date.isBlank()) continue;
            Double ctl = dbl(n, "ctl");
            Double atl = dbl(n, "atl");
            Double form = ctl != null && atl != null ? ctl - atl : null;
            out.add(new IntervalsWellnessPoint(date, ctl, atl, form, dbl(n, "rampRate")));
        }
        out.sort(Comparator.comparing(IntervalsWellnessPoint::date));
        return out;
    }

    // ---- Athlet: Schwellenwerte und Zonen -------------------------------------------------------

    /** Name, Gewicht und Rad-Schwellenwerte aus {@code GET /athlete/{id}}. */
    public static IntervalsAthleteThresholds thresholds(JsonNode athlete) {
        String name = text(athlete, "name");
        double weight = athlete.path("icu_weight").asDouble(0);
        Integer ftp = null, indoorFtp = null, lthr = null, maxHr = null;
        JsonNode setting = rideSetting(athlete);
        if (setting != null) {
            ftp = integer(setting, "ftp");
            indoorFtp = integer(setting, "indoor_ftp");
            lthr = integer(setting, "lthr");
            maxHr = integer(setting, "max_hr");
        }
        return new IntervalsAthleteThresholds(name, ftp, indoorFtp, lthr, maxHr, weight > 0 ? weight : null);
    }

    /**
     * Schwellenwerte plus Power- und HF-Zonen aus den Rad-Einstellungen ({@code sportSettings}).
     * Power-Zonen: Prozent der FTP (55, 75, ...), Sweet Spot wird immer ergaenzt.
     * HF-Zonen: absolute bpm-Grenzen, bezogen auf LTHR oder (hr_zones_max_hr) MaxHR.
     */
    public static IntervalsAthleteZones athleteZones(JsonNode athlete) {
        IntervalsAthleteThresholds thresholds = thresholds(athlete);
        List<Double> powerPercents = List.of();
        List<String> powerNames = List.of();
        List<Double> hrLimits = List.of();
        List<String> hrNames = List.of();
        boolean hrZonesAreMaxHr = false;

        JsonNode setting = rideSetting(athlete);
        if (setting != null) {
            powerPercents = doubles(setting.path("power_zones"));
            powerNames = strings(setting.path("power_zone_names"));
            hrLimits = doubles(setting.path("hr_zones"));
            hrNames = strings(setting.path("hr_zone_names"));
            hrZonesAreMaxHr = setting.path("hr_zones_max_hr").asBoolean(false);
        }

        Integer ftp = thresholds.ftp();
        List<IntervalsZone> powerZones = ftp != null && ftp > 0
                ? powerZones(powerPercents, powerNames, ftp)
                : List.of();

        String hrReference = hrZonesAreMaxHr ? "max_hr" : "lthr";
        Integer reference = hrZonesAreMaxHr ? thresholds.maxHr() : thresholds.lthr();
        List<IntervalsZone> hrZones = reference != null && reference > 0
                ? hrZones(hrLimits, hrNames, reference)
                : List.of();

        return new IntervalsAthleteZones(thresholds, powerZones, hrZones, hrReference);
    }

    /**
     * ID des sportSettings-Eintrags fuer Rad (dieselbe Auswahl wie beim Lesen der Schwellenwerte);
     * null, wenn es keinen gibt.
     */
    public static String rideSettingId(JsonNode athlete) {
        JsonNode setting = rideSetting(athlete);
        if (setting == null) return null;
        JsonNode id = setting.path("id");
        return id.isNumber() || id.isTextual() ? id.asText() : null;
    }

    /** Erster sportSettings-Eintrag fuer Ride oder VirtualRide; null, wenn keiner. */
    private static JsonNode rideSetting(JsonNode athlete) {
        JsonNode sportSettings = athlete.path("sportSettings");
        if (!sportSettings.isArray()) return null;
        for (JsonNode setting : sportSettings) {
            for (JsonNode t : setting.path("types")) {
                if ("Ride".equals(t.asText()) || "VirtualRide".equals(t.asText())) {
                    return setting;
                }
            }
        }
        return null;
    }

    private static List<IntervalsZone> powerZones(List<Double> percentages, List<String> names, int ftp) {
        if (percentages.isEmpty() && names.isEmpty()) return List.of();

        List<IntervalsZone> zones = new ArrayList<>();
        int regularZones = Math.min(7, Math.max(names.size(), percentages.size()));
        for (int i = 0; i < regularZones; i++) {
            double lowerPct = i == 0 ? 0.0 : safeGet(percentages, i - 1) / 100.0;
            Double upperPct = i < percentages.size() ? percentages.get(i) / 100.0 : null;
            if (i == regularZones - 1 && regularZones == 7) upperPct = null; // Z7 nach oben offen
            Integer upperValue = upperPct == null ? null : (int) Math.round(upperPct * ftp);
            String name = i < names.size() ? names.get(i) : "Z" + (i + 1);
            zones.add(new IntervalsZone(i + 1, name, lowerPct, upperPct, (int) Math.round(lowerPct * ftp),
                    upperValue, color(i)));
        }

        // Sweet Spot zeigt intervals.icu in der Oberflaeche, liefert ihn aber nicht immer ueber die API.
        boolean hasSweetSpot = false;
        for (int i = regularZones; i < names.size(); i++) {
            String name = names.get(i);
            if (name != null && (name.equalsIgnoreCase("Sweet Spot") || name.equalsIgnoreCase("SweetSpot")
                    || name.equalsIgnoreCase("SS"))) {
                hasSweetSpot = true;
                break;
            }
        }
        if (!hasSweetSpot) {
            zones.add(new IntervalsZone(8, "Sweet Spot", 0.84, 0.97,
                    (int) Math.round(0.84 * ftp), (int) Math.round(0.97 * ftp), "#66bb6a"));
        }
        return zones;
    }

    private static List<IntervalsZone> hrZones(List<Double> bpmLimits, List<String> names, int referenceHr) {
        int n = Math.max(names.size(), bpmLimits.size());
        List<IntervalsZone> zones = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int lowerValue = i == 0 ? 0 : (int) Math.round(safeGet(bpmLimits, i - 1)) + 1;
            Integer upperValue = i < bpmLimits.size() && bpmLimits.get(i) != null
                    ? (int) Math.round(bpmLimits.get(i)) : null;
            if (i == n - 1) upperValue = null; // letzte Zone nach oben offen
            double lowerPct = (double) lowerValue / referenceHr;
            Double upperPct = upperValue != null ? (double) upperValue / referenceHr : null;
            String name = i < names.size() ? names.get(i) : "Z" + (i + 1);
            zones.add(new IntervalsZone(i + 1, name, lowerPct, upperPct, lowerValue, upperValue, color(i)));
        }
        return zones;
    }

    // ---- Zonenzeiten einer Aktivitaet ---------------------------------------------------------

    /**
     * Zonenverteilung aus den vorberechneten Zeiten im Aktivitaets-Detail ({@code GET /activity/{id}}),
     * damit die Werte genau zur intervals.icu-Website passen. Namen und Farben aus den Athleten-Zonen.
     * Power: icu_zone_times = [{"id":"Z1","secs":..}, ..., {"id":"SS",..}], Sweet Spot zaehlt nicht mit
     * (ueberlappt Z2-Z4). HF: icu_hr_zone_times = [secs, secs, ...], Index = Zone.
     */
    public static IntervalsActivityZones activityZones(JsonNode activity, IntervalsAthleteZones athleteZones) {
        List<IntervalsZoneShare> power = powerDistribution(activity.path("icu_zone_times"), athleteZones.powerZones());
        List<IntervalsZoneShare> hr = hrDistribution(activity.path("icu_hr_zone_times"), athleteZones.hrZones());
        JsonNode trainer = activity.path("trainer");
        boolean indoor = "VirtualRide".equalsIgnoreCase(text(activity, "type"))
                || (trainer.isBoolean() && trainer.asBoolean());
        return new IntervalsActivityZones(power, hr, indoor, integer(activity, "icu_ftp"));
    }

    private static List<IntervalsZoneShare> powerDistribution(JsonNode zoneTimes, List<IntervalsZone> defs) {
        if (!zoneTimes.isArray() || zoneTimes.isEmpty()) return List.of();
        List<String> ids = new ArrayList<>();
        List<Integer> secs = new ArrayList<>();
        long total = 0;
        for (JsonNode n : zoneTimes) {
            String id = n.path("id").asText("");
            if (id.equalsIgnoreCase("SS")) continue;
            int s = n.path("secs").asInt(0);
            ids.add(id);
            secs.add(s);
            total += s;
        }
        return distribution(ids, secs, total, defs);
    }

    private static List<IntervalsZoneShare> hrDistribution(JsonNode zoneTimes, List<IntervalsZone> defs) {
        if (!zoneTimes.isArray() || zoneTimes.isEmpty()) return List.of();
        List<String> ids = new ArrayList<>();
        List<Integer> secs = new ArrayList<>();
        long total = 0;
        int i = 0;
        for (JsonNode n : zoneTimes) {
            int s = n.asInt(0);
            ids.add("Z" + (++i));
            secs.add(s);
            total += s;
        }
        return distribution(ids, secs, total, defs);
    }

    private static List<IntervalsZoneShare> distribution(List<String> ids, List<Integer> secs, long total,
                                                         List<IntervalsZone> defs) {
        Map<Integer, IntervalsZone> byIndex = new HashMap<>();
        for (IntervalsZone d : defs) byIndex.put(d.index(), d);

        List<IntervalsZoneShare> out = new ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            IntervalsZone def = byIndex.get(zoneNumber(id, i + 1));
            String label = def != null && def.name() != null ? def.name() : "";
            String color = def != null && def.color() != null ? def.color() : color(i);
            int s = secs.get(i);
            double pct = total > 0 ? (double) s / total * 100.0 : 0.0;
            out.add(new IntervalsZoneShare(id, label, s, Math.round(pct * 10.0) / 10.0, color));
        }
        return out;
    }

    /** "Z3" -> 3; sonst {@code fallback}. */
    private static int zoneNumber(String id, int fallback) {
        if (id != null && id.length() > 1 && (id.charAt(0) == 'Z' || id.charAt(0) == 'z')) {
            try {
                return Integer.parseInt(id.substring(1));
            } catch (NumberFormatException ignored) {
                // fallback
            }
        }
        return fallback;
    }

    // ---- Power-Curve -------------------------------------------------------------------------

    /**
     * Antwort von {@code GET /activity/{id}/power-curve}, defensiv gelesen. Akzeptiert:
     * Array von {"secs"|"duration": n, "value"|"watts"|"power": w}, parallele Arrays
     * {"secs": [...], "values"|"watts": [...]} oder eine Map {"5": 510, "60": 278}.
     */
    public static IntervalsPowerCurve powerCurve(JsonNode root) {
        TreeMap<Integer, Integer> out = new TreeMap<>();
        if (root == null || root.isMissingNode() || root.isNull()) return toCurve(out);

        if (root.isArray()) {
            for (JsonNode n : root) {
                Integer secs = firstInt(n, "secs", "duration", "t", "x");
                Integer watts = firstInt(n, "value", "watts", "power", "y");
                if (secs != null && secs > 0 && watts != null && watts >= 0) {
                    out.put(secs, watts);
                }
            }
        } else if (root.isObject()) {
            JsonNode secsArr = firstNode(root, "secs", "durations", "x");
            JsonNode wattsArr = firstNode(root, "values", "watts", "power", "y");
            if (secsArr != null && secsArr.isArray() && wattsArr != null && wattsArr.isArray()) {
                int n = Math.min(secsArr.size(), wattsArr.size());
                for (int i = 0; i < n; i++) {
                    JsonNode s = secsArr.get(i);
                    JsonNode w = wattsArr.get(i);
                    if (!s.isNull() && !w.isNull()) {
                        out.put(s.asInt(), w.asInt());
                    }
                }
            } else {
                root.fields().forEachRemaining(e -> {
                    Integer secs = parseInt(e.getKey());
                    if (secs != null && e.getValue().isNumber()) {
                        out.put(secs, e.getValue().asInt());
                    }
                });
            }
        }
        return toCurve(out);
    }

    /** Ersatz aus dem Aktivitaets-Detail: Felder "icu_pm_p_&lt;sekunden&gt;" und die Map "icu_pm". */
    public static IntervalsPowerCurve powerCurveFromActivity(JsonNode activity) {
        TreeMap<Integer, Integer> out = new TreeMap<>();
        if (activity == null || !activity.isObject()) return toCurve(out);

        activity.fields().forEachRemaining(e -> {
            if (e.getKey().startsWith("icu_pm_p_")) {
                Integer secs = parseInt(e.getKey().substring("icu_pm_p_".length()));
                if (secs != null && secs > 0 && e.getValue().isNumber() && e.getValue().asInt() > 0) {
                    out.put(secs, e.getValue().asInt());
                }
            }
        });
        JsonNode pm = activity.path("icu_pm");
        if (pm.isObject()) {
            pm.fields().forEachRemaining(e -> {
                Integer secs = parseInt(e.getKey());
                if (secs != null && e.getValue().isNumber() && e.getValue().asInt() > 0) {
                    out.putIfAbsent(secs, e.getValue().asInt());
                }
            });
        }
        return toCurve(out);
    }

    private static IntervalsPowerCurve toCurve(TreeMap<Integer, Integer> points) {
        List<IntervalsPowerCurve.Point> list = new ArrayList<>(points.size());
        points.forEach((secs, watts) -> list.add(new IntervalsPowerCurve.Point(secs, watts)));
        return new IntervalsPowerCurve(list);
    }

    // ---- Helfer ------------------------------------------------------------------------------

    private static String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    private static Double dbl(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isNumber() ? v.asDouble() : null;
    }

    private static Integer integer(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isNumber() ? v.asInt() : null;
    }

    private static Integer firstInt(JsonNode obj, String... keys) {
        if (obj == null || !obj.isObject()) return null;
        for (String k : keys) {
            JsonNode v = obj.path(k);
            if (v.isNumber()) return v.asInt();
        }
        return null;
    }

    private static JsonNode firstNode(JsonNode obj, String... keys) {
        for (String k : keys) {
            JsonNode v = obj.path(k);
            if (!v.isMissingNode() && !v.isNull()) return v;
        }
        return null;
    }

    private static Integer parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<Double> doubles(JsonNode node) {
        if (node == null || !node.isArray()) return Collections.emptyList();
        List<Double> list = new ArrayList<>(node.size());
        for (JsonNode n : node) list.add(n.isNull() ? null : n.asDouble());
        return list;
    }

    private static List<Integer> ints(JsonNode node) {
        if (node == null || !node.isArray()) return Collections.emptyList();
        List<Integer> list = new ArrayList<>(node.size());
        for (JsonNode n : node) list.add(n.isNull() ? null : n.asInt());
        return list;
    }

    private static List<String> strings(JsonNode node) {
        if (node == null || !node.isArray()) return Collections.emptyList();
        List<String> list = new ArrayList<>(node.size());
        for (JsonNode n : node) list.add(n.isNull() ? null : n.asText());
        return list;
    }

    private static double safeGet(List<Double> list, int idx) {
        return idx >= 0 && idx < list.size() && list.get(idx) != null ? list.get(idx) : 0.0;
    }

    private static String color(int index) {
        return ZONE_COLORS[Math.min(index, ZONE_COLORS.length - 1)];
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T v : values) {
            if (v != null) return v;
        }
        return null;
    }
}
