package com.cycling.report.map;

import com.cycling.report.ReportContent.Streams;
import com.cycling.report.ReportContent.ZoneBand;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Faerbt den Fahrtverlauf ein: nach Leistungszonen, wenn Wattdaten und Zonen da sind, sonst nach Tempo, sonst
 * einfarbig. Die Werte werden vorher geglaettet (gleitender Mittelwert ueber {@link #SMOOTH_HALF_WINDOW} Punkte
 * je Seite), sonst flackert die Linie bei jeder Leistungsspitze. Dieselbe Logik steckt im Frontend
 * ({@code route-coloring.ts}).
 */
public final class RouteColoring {

    /** Halbe Fensterbreite der Glaettung; bei 1 Punkt pro Sekunde rund 30 s. */
    static final int SMOOTH_HALF_WINDOW = 15;
    /** Farbe fuer Punkte ohne Messwert. */
    static final String NO_VALUE_COLOR = "#cfd8dc";
    static final String SINGLE_COLOR = "#3f51b5";
    /** Tempo-Stufen von langsam bis schnell. */
    static final String[] SPEED_COLORS = {"#3b82f6", "#06b6d4", "#22c55e", "#eab308", "#f97316", "#ef4444"};

    private RouteColoring() {
    }

    /** Ein GPS-Punkt mit Farbe (Index in {@link ColoredRoute#colors()}). */
    public record Point(double lat, double lng, int color) {
    }

    public record LegendEntry(String color, String label) {
    }

    /**
     * @param points nur Punkte mit gueltigen Koordinaten, in Fahrtreihenfolge
     * @param colors Farbtabelle (Hex)
     */
    public record ColoredRoute(List<Point> points, List<String> colors, String legendTitle, List<LegendEntry> legend) {
    }

    /** null, wenn die Fahrt weniger als zwei gueltige GPS-Punkte hat (Indoor). */
    public static ColoredRoute color(Streams streams, List<ZoneBand> powerBands) {
        List<Double> lat = streams.lat();
        List<Double> lng = streams.lng();
        if (lat == null || lng == null) return null;
        int n = Math.min(lat.size(), lng.size());

        if (hasAny(streams.watts()) && powerBands != null && !powerBands.isEmpty()) {
            return byZones(lat, lng, n, smooth(streams.watts(), n), powerBands);
        }
        if (hasAny(streams.velocity())) {
            return bySpeed(lat, lng, n, smooth(streams.velocity(), n));
        }
        return build(lat, lng, n, i -> 0, List.of(SINGLE_COLOR), null, List.of());
    }

    private static ColoredRoute byZones(List<Double> lat, List<Double> lng, int n, Double[] watts,
                                        List<ZoneBand> bands) {
        List<String> colors = new ArrayList<>();
        List<LegendEntry> legend = new ArrayList<>();
        for (int z = 0; z < bands.size(); z++) {
            ZoneBand b = bands.get(z);
            colors.add(b.color());
            String range = b.upper() == null ? "≥ " + b.lower() + " W" : b.lower() + "–" + b.upper() + " W";
            legend.add(new LegendEntry(b.color(), zoneLabel(b.name(), z) + " (" + range + ")"));
        }
        int none = colors.size();
        colors.add(NO_VALUE_COLOR);
        return build(lat, lng, n, i -> {
            Double w = watts[i];
            if (w == null) return none;
            return zoneIndex(bands, w, none);
        }, colors, "Leistungszonen", legend);
    }

    /** "Z2 Endurance"; traegt der Name die Nummer schon ("Z2"), bleibt er unveraendert. */
    static String zoneLabel(String name, int index) {
        String prefix = "Z" + (index + 1);
        if (name == null || name.isBlank()) return prefix;
        return name.matches("(?i)z[0-9].*") ? name : prefix + " " + name;
    }

    static int zoneIndex(List<ZoneBand> bands, double value, int fallback) {
        for (int z = 0; z < bands.size(); z++) {
            ZoneBand b = bands.get(z);
            if (value >= b.lower() && (b.upper() == null || value < b.upper())) return z;
        }
        // Unter der ersten Grenze -> erste Zone, ueber allen -> letzte
        if (!bands.isEmpty() && value < bands.getFirst().lower()) return 0;
        return bands.isEmpty() ? fallback : bands.size() - 1;
    }

    private static ColoredRoute bySpeed(List<Double> lat, List<Double> lng, int n, Double[] velocity) {
        double[] kmh = Arrays.stream(velocity).filter(v -> v != null).mapToDouble(v -> v * 3.6).sorted().toArray();
        double lo = percentile(kmh, 0.05);
        double hi = percentile(kmh, 0.95);
        int steps = SPEED_COLORS.length;
        double step = (hi - lo) / steps;

        List<String> colors = new ArrayList<>(Arrays.asList(SPEED_COLORS));
        int none = colors.size();
        colors.add(NO_VALUE_COLOR);
        List<LegendEntry> legend = new ArrayList<>();
        if (step < 0.5) {
            // Praktisch konstantes Tempo: eine Farbe
            legend.add(new LegendEntry(SPEED_COLORS[2], fmt(lo) + " km/h"));
            return build(lat, lng, n, i -> velocity[i] == null ? none : 2, colors, "Tempo", legend);
        }
        for (int s = 0; s < steps; s++) {
            String label = s == 0 ? "< " + fmt(lo + step) + " km/h"
                    : s == steps - 1 ? "≥ " + fmt(lo + step * s) + " km/h"
                    : fmt(lo + step * s) + "–" + fmt(lo + step * (s + 1)) + " km/h";
            legend.add(new LegendEntry(SPEED_COLORS[s], label));
        }
        return build(lat, lng, n, i -> {
            Double v = velocity[i];
            if (v == null) return none;
            int s = (int) Math.floor((v * 3.6 - lo) / step);
            return Math.max(0, Math.min(steps - 1, s));
        }, colors, "Tempo", legend);
    }

    private interface ColorOf {
        int at(int index);
    }

    private static ColoredRoute build(List<Double> lat, List<Double> lng, int n, ColorOf colorOf,
                                      List<String> colors, String title, List<LegendEntry> legend) {
        List<Point> points = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Double la = lat.get(i);
            Double lo = lng.get(i);
            if (!validCoordinate(la, lo)) continue;
            points.add(new Point(la, lo, colorOf.at(i)));
        }
        if (points.size() < 2) return null;
        return new ColoredRoute(List.copyOf(points), List.copyOf(colors), title, List.copyOf(legend));
    }

    /** intervals.icu liefert bei fehlendem Fix teils 0/0; das ist kein echter Punkt. */
    static boolean validCoordinate(Double lat, Double lng) {
        if (lat == null || lng == null) return false;
        if (lat == 0.0 && lng == 0.0) return false;
        return lat >= -85 && lat <= 85 && lng >= -180 && lng <= 180;
    }

    /** Gleitender Mittelwert; Luecken (null) zaehlen nicht mit, ein Punkt ohne Nachbarwerte bleibt null. */
    static Double[] smooth(List<? extends Number> values, int n) {
        Double[] out = new Double[n];
        if (values == null) return out;
        int size = Math.min(n, values.size());
        double[] prefix = new double[size + 1];
        int[] counts = new int[size + 1];
        for (int i = 0; i < size; i++) {
            Number v = values.get(i);
            prefix[i + 1] = prefix[i] + (v != null ? v.doubleValue() : 0);
            counts[i + 1] = counts[i] + (v != null ? 1 : 0);
        }
        for (int i = 0; i < size; i++) {
            int from = Math.max(0, i - SMOOTH_HALF_WINDOW);
            int to = Math.min(size, i + SMOOTH_HALF_WINDOW + 1);
            int c = counts[to] - counts[from];
            out[i] = c > 0 ? (prefix[to] - prefix[from]) / c : null;
        }
        return out;
    }

    private static double percentile(double[] sorted, double p) {
        if (sorted.length == 0) return 0;
        int idx = (int) Math.round(p * (sorted.length - 1));
        return sorted[Math.max(0, Math.min(sorted.length - 1, idx))];
    }

    private static boolean hasAny(List<? extends Number> values) {
        if (values == null) return false;
        for (Number v : values) if (v != null) return true;
        return false;
    }

    private static String fmt(double v) {
        return String.format(Locale.GERMAN, "%.0f", v);
    }
}
