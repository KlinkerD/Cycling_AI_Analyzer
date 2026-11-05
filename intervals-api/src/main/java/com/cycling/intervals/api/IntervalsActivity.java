package com.cycling.intervals.api;

import com.cycling.types.ActivityId;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Eine Rad-Aktivitaet. Die Kennzahlen bevorzugen die von intervals.icu berechneten {@code icu_*}-Werte
 * und fallen sonst auf die Originalwerte (z. B. von Strava) zurueck.
 *
 * @param startDateLocal lokale Startzeit (ohne Zone), z. B. 2026-11-01T08:30
 * @param distance       Meter
 * @param movingTime     Sekunden
 * @param elapsedTime    Sekunden
 * @param averageSpeed   m/s
 * @param trainingLoad   TSS laut intervals.icu ({@code icu_training_load}); null, wenn nicht berechnet
 * @param intensityFactor IF laut intervals.icu ({@code icu_intensity} / 100, z. B. 0.78); null, wenn nicht berechnet
 * @param startDate      Startzeit in UTC ({@code start_date}); null, wenn unbekannt
 */
public record IntervalsActivity(
        ActivityId id,
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
        Double trainingLoad,
        Double intensityFactor,
        Instant startDate
) {
}
