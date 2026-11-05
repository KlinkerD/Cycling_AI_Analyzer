package com.cycling.weather;

import java.util.List;

/**
 * Modellreihe eines Ortes: Werte im festen Takt, alle Listen indexgleich zu {@code epochSeconds}
 * (aufsteigend, UTC). Einzelne Werte koennen null sein.
 *
 * @param stepMinutes   Takt der Reihe (15 bei {@code minutely_15}, 60 bei {@code hourly})
 * @param precipitation Niederschlag in mm, Summe des Intervalls, das zum Zeitstempel ENDET (Open-Meteo-Konvention)
 */
record Series(
        int stepMinutes,
        List<Long> epochSeconds,
        List<Double> temperatureC,
        List<Double> windSpeedKmh,
        List<Double> windGustKmh,
        List<Double> windDirectionDeg,
        List<Double> precipitation
) {

    static Series empty(int stepMinutes) {
        return new Series(stepMinutes, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
