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
 * @param lat      Breitengrad in Grad (aus dem intervals.icu-Stream "latlng", Feld "data"); leer ohne GPS
 * @param lng      Laengengrad in Grad (aus dem intervals.icu-Stream "latlng", Feld "data2"); leer ohne GPS
 */
public record IntervalsStreams(
        List<Double> distance,
        List<Integer> watts,
        List<Integer> heartrate,
        List<Double> velocity,
        List<Double> altitude,
        List<Integer> time,
        List<Integer> cadence,
        List<Double> lat,
        List<Double> lng
) {
}
