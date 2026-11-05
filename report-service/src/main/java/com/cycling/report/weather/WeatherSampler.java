package com.cycling.report.weather;

import com.cycling.report.api.StreamDataDto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Waehlt aus den Streams einer Fahrt die Punkte fuer die Wetterabfrage: alle {@link #INTERVAL_SECONDS} ab dem
 * Start und zusaetzlich das Ende (so hat auch eine kurze Fahrt mindestens Start und Ende). Rein funktional.
 *
 * <p>Fehlt am Zielzeitpunkt die GPS-Position (Tunnel, Aussetzer), zaehlt die naechste gueltige Position innerhalb
 * von {@link #MAX_GPS_GAP_SECONDS}; gibt es keine, faellt der Punkt weg.
 */
public final class WeatherSampler {

    public static final int INTERVAL_SECONDS = 600;
    static final int MAX_GPS_GAP_SECONDS = 120;
    /** Das Ende kommt nur dazu, wenn es mindestens so weit nach dem letzten Takt-Punkt liegt. */
    static final int MIN_END_GAP_SECONDS = 60;
    /** Fahrtrichtung aus der Strecke so weit vor und nach dem Punkt. */
    static final double COURSE_HALF_WINDOW_M = 250;
    /** Darunter (Stillstand, enge Kehre) keine Fahrtrichtung. */
    static final double MIN_COURSE_DISTANCE_M = 100;

    private WeatherSampler() {
    }

    /** @param start Startzeit der Fahrt in UTC */
    public static List<WeatherSample> sample(StreamDataDto streams, Instant start) {
        List<Integer> time = streams.time();
        List<Double> lat = streams.lat();
        List<Double> lng = streams.lng();
        if (start == null || time == null || lat == null || lng == null || time.isEmpty()) return List.of();

        Integer end = null;
        for (int i = time.size() - 1; i >= 0 && end == null; i--) end = time.get(i);
        if (end == null) return List.of();

        List<Integer> targets = new ArrayList<>();
        for (int t = 0; t <= end; t += INTERVAL_SECONDS) targets.add(t);
        if (end - targets.getLast() >= MIN_END_GAP_SECONDS) targets.add(end);

        List<WeatherSample> out = new ArrayList<>(targets.size());
        for (int target : targets) {
            int index = nearestWithGps(streams, target);
            if (index < 0) continue;
            Double distanceM = value(streams.distance(), index);
            out.add(new WeatherSample(target / 60,
                    distanceM == null ? null : Math.round(distanceM / 10.0) / 100.0,
                    lat.get(index), lng.get(index), start.plusSeconds(target),
                    course(streams, index, distanceM)));
        }
        return out;
    }

    /** Index mit gueltiger Position, dessen Zeit dem Ziel am naechsten liegt (hoechstens 2 min weg); sonst -1. */
    private static int nearestWithGps(StreamDataDto s, int target) {
        int best = -1;
        int bestGap = Integer.MAX_VALUE;
        List<Integer> time = s.time();
        for (int i = 0; i < time.size(); i++) {
            Integer t = time.get(i);
            if (t == null || !hasGps(s, i)) continue;
            int gap = Math.abs(t - target);
            if (gap < bestGap) {
                best = i;
                bestGap = gap;
            }
            if (t > target + MAX_GPS_GAP_SECONDS) break;
        }
        return bestGap <= MAX_GPS_GAP_SECONDS ? best : -1;
    }

    /**
     * Fahrtrichtung von der Position ~250 m vor dem Punkt zur Position ~250 m danach (an Start und Ende entsprechend
     * einseitig). Null ohne Distanz-Stream oder bei weniger als 100 m Abstand.
     */
    private static Double course(StreamDataDto s, int index, Double distanceM) {
        if (distanceM == null) return null;
        int from = index;
        int to = index;
        for (int i = index - 1; i >= 0; i--) {
            Double d = value(s.distance(), i);
            if (d == null || !hasGps(s, i)) continue;
            from = i;
            if (distanceM - d >= COURSE_HALF_WINDOW_M) break;
        }
        for (int i = index + 1; i < s.lat().size(); i++) {
            Double d = value(s.distance(), i);
            if (d == null || !hasGps(s, i)) continue;
            to = i;
            if (d - distanceM >= COURSE_HALF_WINDOW_M) break;
        }
        Double dFrom = value(s.distance(), from);
        Double dTo = value(s.distance(), to);
        if (dFrom == null || dTo == null || dTo - dFrom < MIN_COURSE_DISTANCE_M) return null;
        return bearing(s.lat().get(from), s.lng().get(from), s.lat().get(to), s.lng().get(to));
    }

    /** Anfangskurs von A nach B in Grad (0 = Nord, 90 = Ost). */
    static double bearing(double lat1, double lng1, double lat2, double lng2) {
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dl = Math.toRadians(lng2 - lng1);
        double y = Math.sin(dl) * Math.cos(p2);
        double x = Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dl);
        double deg = Math.toDegrees(Math.atan2(y, x));
        return (deg + 360) % 360;
    }

    private static boolean hasGps(StreamDataDto s, int i) {
        return value(s.lat(), i) != null && value(s.lng(), i) != null;
    }

    private static <T> T value(List<T> list, int i) {
        return list != null && i >= 0 && i < list.size() ? list.get(i) : null;
    }
}
