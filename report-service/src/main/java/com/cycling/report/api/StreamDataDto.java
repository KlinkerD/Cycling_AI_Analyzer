package com.cycling.report.api;

import java.util.List;

/**
 * Enthält die Zeitreihendaten (Streams) einer Aktivität von intervals.icu, geliefert vom
 * intervals-service (der dort auch das Rohformat von intervals.icu liest).
 */
public record StreamDataDto(
        List<Double> distance,   // in Metern
        List<Integer> watts,     // Leistung in Watt (kann null-Werte enthalten)
        List<Integer> heartrate, // Herzfrequenz in bpm
        List<Double> velocity,   // Geschwindigkeit in m/s
        List<Double> altitude,   // Höhe in Metern
        List<Integer> time,      // Zeit in Sekunden
        List<Integer> cadence    // Trittfrequenz in rpm
) {}

