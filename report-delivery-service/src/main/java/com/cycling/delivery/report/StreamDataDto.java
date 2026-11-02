package com.cycling.delivery.report;

import java.util.List;

/**
 * Enthält die Zeitreihendaten (Streams) einer Aktivität von intervals.icu.
 * Endpoint: GET /api/v1/activity/{id}/streams
 *
 * intervals.icu gibt die Streams als Array von Objekten zurück:
 * [ { "type": "watts", "data": [...] }, { "type": "heartrate", "data": [...] }, ... ]
 * Diese Klasse ist das aufbereitete Ergebnis nach der Verarbeitung.
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

