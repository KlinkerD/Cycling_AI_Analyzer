package com.cycling.messaging.contract;

import com.cycling.types.ActivityId;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * intervals-service -> report-service: neue Rad-Aktivitaet bei intervals.icu gefunden. Enthaelt die
 * Zusammenfassung, damit der report-service die Analyse ohne weiteren Abruf starten kann.
 * Kann mehrfach kommen (z. B. nach einem Neustart des intervals-service) - Empfaenger deduplizieren
 * ueber {@code activityId}.
 *
 * @param startDateLocal lokale Startzeit (ohne Zone), im JSON z. B. "2026-11-01T08:30:00"
 * @param distance       Meter
 * @param movingTime     Sekunden
 * @param elapsedTime    Sekunden
 * @param averageSpeed   m/s
 * @param startDate      Startzeit in UTC, im JSON z. B. "2026-11-01T07:30:00Z"; null bei alten Meldungen
 */
public record ActivityDetected(
        ActivityId activityId,
        String name,
        LocalDateTime startDateLocal,
        String type,
        Double distance,
        Integer movingTime,
        Integer elapsedTime,
        Double averageWatts,
        Double normalizedPower,
        Double maxWatts,
        Double averageHeartrate,
        Double maxHeartrate,
        Double averageSpeed,
        Double totalElevationGain,
        Instant startDate
) {
}
