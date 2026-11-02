package com.cycling.messaging.contract;

/**
 * Kennzahlen einer Fahrt, wie sie die KI fuer Bewertung und Vergleich braucht.
 * Alle Werte optional (null = unbekannt), {@code activityDate} als ISO-Datum/Zeit.
 */
public record WorkoutData(
        String activityName,
        String activityDate,
        Boolean indoor,
        Double distanceKm,
        Integer durationSeconds,
        Double avgPowerWatts,
        Double maxPowerWatts,
        Double avgHeartRate,
        Double maxHeartRate,
        Double avgSpeedKmh,
        Double elevationGainM,
        Double avgCadenceRpm,
        Integer ftpUsed,
        Double intensityFactor,
        Integer overallScore
) {
}
