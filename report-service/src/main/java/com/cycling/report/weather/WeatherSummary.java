package com.cycling.report.weather;

import com.cycling.report.api.WeatherPointDto;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Kennzahlen des Wetters einer Fahrt (Prompt, Vergleich mit frueheren Fahrten, Hinweis im Chart).
 * Fehlende Werte werden ausgelassen; ohne einen einzigen Temperaturwert gibt es keine Zusammenfassung.
 *
 * @param precipitationMm Niederschlag gesamt: Intensitaet mal Abstand zum vorherigen Punkt, aufsummiert
 */
public record WeatherSummary(
        double minTemperatureC,
        double maxTemperatureC,
        double avgTemperatureC,
        Double avgWindSpeedKmh,
        Double maxWindGustKmh,
        double precipitationMm
) {

    public static Optional<WeatherSummary> of(List<WeatherPointDto> points) {
        if (points == null) return Optional.empty();
        List<Double> temps = points.stream().map(WeatherPointDto::temperatureC).filter(Objects::nonNull).toList();
        if (temps.isEmpty()) return Optional.empty();

        List<Double> winds = points.stream().map(WeatherPointDto::windSpeedKmh).filter(Objects::nonNull).toList();
        Double maxGust = points.stream().map(WeatherPointDto::windGustKmh).filter(Objects::nonNull)
                .max(Double::compare).orElse(null);

        double precip = 0;
        for (int i = 1; i < points.size(); i++) {
            Double rate = points.get(i).precipitationMmPerHour();
            if (rate == null) continue;
            int minutes = points.get(i).minuteOfRide() - points.get(i - 1).minuteOfRide();
            precip += rate * minutes / 60.0;
        }

        return Optional.of(new WeatherSummary(
                temps.stream().mapToDouble(Double::doubleValue).min().orElseThrow(),
                temps.stream().mapToDouble(Double::doubleValue).max().orElseThrow(),
                round1(temps.stream().mapToDouble(Double::doubleValue).average().orElseThrow()),
                winds.isEmpty() ? null : round1(winds.stream().mapToDouble(Double::doubleValue).average().orElseThrow()),
                maxGust,
                round1(precip)));
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
