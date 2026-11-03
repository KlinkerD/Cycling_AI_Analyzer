package com.cycling.intervals.api;

import java.util.List;

/**
 * Zeitreihen einer Aktivitaet (gleicher Index = gleicher Zeitpunkt). Fehlt ein Stream, ist die Liste leer;
 * einzelne Werte koennen null sein.
 *
 * @param distance Meter
 * @param velocity m/s
 * @param altitude Meter
 * @param time     Sekunden seit Start
 */
public record IntervalsStreams(
        List<Double> distance,
        List<Integer> watts,
        List<Integer> heartrate,
        List<Double> velocity,
        List<Double> altitude,
        List<Integer> time,
        List<Integer> cadence
) {
}
