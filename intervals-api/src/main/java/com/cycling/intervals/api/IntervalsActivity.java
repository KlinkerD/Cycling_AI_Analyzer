package com.cycling.intervals.api;

/**
 * Eine Rad-Aktivitaet. Die Kennzahlen bevorzugen die von intervals.icu berechneten {@code icu_*}-Werte
 * und fallen sonst auf die Originalwerte (z. B. von Strava) zurueck.
 *
 * @param startDateLocal lokale Startzeit, z. B. "2026-11-01T08:30:00"
 * @param distance       Meter
 * @param movingTime     Sekunden
 * @param elapsedTime    Sekunden
 * @param averageSpeed   m/s
 */
public record IntervalsActivity(
        String id,
        String name,
        String startDateLocal,
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
        Double totalElevationGain
) {
}
