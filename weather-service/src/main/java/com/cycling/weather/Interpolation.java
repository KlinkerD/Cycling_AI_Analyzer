package com.cycling.weather;

import com.cycling.weather.api.WeatherResult;

import java.time.Instant;
import java.util.List;

/**
 * Werte einer {@link Series} zu einem beliebigen Zeitpunkt. Rein funktional.
 *
 * <ul>
 *   <li>Temperatur, Wind, Boeen: linear zwischen den beiden Nachbarwerten.</li>
 *   <li>Windrichtung: ueber Einheitsvektoren, damit 350 und 10 Grad zu 0 Grad werden und nicht zu 180.</li>
 *   <li>Niederschlag: Summe des Intervalls, in dem der Zeitpunkt liegt, als Intensitaet in mm/h.</li>
 * </ul>
 * Liegt der Zeitpunkt ausserhalb der Reihe, sind alle Werte null.
 */
final class Interpolation {

    private Interpolation() {
    }

    static WeatherResult.Point at(Series series, double lat, double lng, Instant time) {
        long epochSecond = time.getEpochSecond();
        List<Long> ts = series.epochSeconds();
        int n = ts.size();
        if (n == 0 || epochSecond < ts.getFirst() || epochSecond > ts.getLast()) {
            return new WeatherResult.Point(lat, lng, time, null, null, null, null, null);
        }

        // i = letzter Index mit ts[i] <= t
        int i = 0;
        while (i + 1 < n && ts.get(i + 1) <= epochSecond) i++;
        int j = Math.min(i + 1, n - 1);
        double f = j == i ? 0 : (double) (epochSecond - ts.get(i)) / (ts.get(j) - ts.get(i));

        // Niederschlag: das Intervall (ts[k-1], ts[k]] mit t darin; genau auf einem Zeitstempel zaehlt das endende
        int k = ts.get(i) == epochSecond ? i : j;
        Double precip = value(series.precipitation(), k);
        Double precipPerHour = precip == null ? null : round(precip * 60.0 / series.stepMinutes(), 2);

        return new WeatherResult.Point(lat, lng, time,
                round(linear(series.temperatureC(), i, j, f), 1),
                round(linear(series.windSpeedKmh(), i, j, f), 1),
                round(linear(series.windGustKmh(), i, j, f), 1),
                round(direction(series.windDirectionDeg(), i, j, f), 0),
                precipPerHour);
    }

    private static Double linear(List<Double> values, int i, int j, double f) {
        Double a = value(values, i);
        Double b = value(values, j);
        if (a == null) return f >= 0.5 ? b : null;
        if (b == null) return f < 0.5 ? a : null;
        return a + (b - a) * f;
    }

    private static Double direction(List<Double> values, int i, int j, double f) {
        Double a = value(values, i);
        Double b = value(values, j);
        if (a == null || b == null) return a != null ? a : b;
        double x = (1 - f) * Math.cos(Math.toRadians(a)) + f * Math.cos(Math.toRadians(b));
        double y = (1 - f) * Math.sin(Math.toRadians(a)) + f * Math.sin(Math.toRadians(b));
        if (Math.abs(x) < 1e-9 && Math.abs(y) < 1e-9) return a; // genau entgegengesetzt: keine Mitte
        double deg = Math.toDegrees(Math.atan2(y, x));
        return deg < 0 ? deg + 360 : deg;
    }

    private static Double value(List<Double> values, int index) {
        return values != null && index >= 0 && index < values.size() ? values.get(index) : null;
    }

    private static Double round(Double v, int digits) {
        if (v == null) return null;
        double factor = Math.pow(10, digits);
        double r = Math.round(v * factor) / factor;
        return digits == 0 && r == 360 ? 0.0 : r;
    }
}
