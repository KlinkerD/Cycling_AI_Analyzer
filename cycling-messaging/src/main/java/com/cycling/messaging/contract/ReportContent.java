package com.cycling.messaging.contract;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Inhalt eines Reports, wie ihn der report-service fuer Nachricht und Charts braucht. Der
 * analysis-service schickt ihn im {@link ReportDeliveryRequest} mit, damit der report-service
 * nichts beim analysis-service nachladen muss. Datumswerte gehen als ISO-String uebers Netz.
 *
 * @param analysis      Kennzahlen und KI-Texte der Fahrt
 * @param streams       Zeitreihen in voller Aufloesung (der report-service duennt sie fuer die Charts aus)
 * @param powerZones    Zeit in den Leistungszonen; leer = Chart entfaellt
 * @param hrZones       Zeit in den Herzfrequenzzonen; leer = Chart entfaellt
 * @param trend         die zuletzt analysierten Fahrten (neueste zuerst)
 * @param powerCurveSet Bestleistungen der Fahrt und der vorherigen; null = Chart entfaellt
 */
public record ReportContent(
        Analysis analysis,
        Streams streams,
        List<Zone> powerZones,
        List<Zone> hrZones,
        List<TrendPoint> trend,
        PowerCurveSet powerCurveSet
) {

    public ReportContent {
        powerZones = powerZones == null ? List.of() : List.copyOf(powerZones);
        hrZones = hrZones == null ? List.of() : List.copyOf(hrZones);
        trend = trend == null ? List.of() : List.copyOf(trend);
    }

    /** Kennzahlen und KI-Bewertung einer Fahrt. Alle Werte optional (null = unbekannt). */
    public record Analysis(
            String activityId,
            String activityName,
            LocalDateTime activityDate,
            Double distanceKm,
            Integer durationSeconds,
            Double avgPowerWatts,
            Double normalizedPowerWatts,
            Double trainingLoad,
            Double intensityFactor,
            Double avgHeartRate,
            Double avgSpeedKmh,
            Double elevationGainM,
            Double avgCadenceRpm,
            Integer overallScore,
            String strengths,
            String anomalies,
            String progress,
            String summary,
            LocalDateTime analyzedAt
    ) {
    }

    /**
     * Zeitreihen der Fahrt, gleich lang und indexgleich. Einzelne Werte koennen null sein,
     * ganze Reihen fehlen (null/leer), wenn das Geraet sie nicht aufgezeichnet hat.
     */
    public record Streams(
            List<Double> distance,   // Meter
            List<Integer> watts,     // Watt
            List<Integer> heartrate, // bpm
            List<Double> velocity,   // m/s
            List<Double> altitude,   // Meter
            List<Integer> time,      // Sekunden
            List<Integer> cadence    // rpm
    ) {
    }

    /** Anteil einer Zone, z. B. "Z2" / "Ausdauer". */
    public record Zone(String zoneName, String zoneLabel, int seconds, double percentage, String color) {
    }

    /** Eine analysierte Fahrt im Trend-Chart. */
    public record TrendPoint(
            String activityId,
            String activityName,
            LocalDateTime activityDate,
            Double avgPowerWatts,
            Double avgHeartRate,
            Double distanceKm,
            Double avgSpeedKmh,
            Integer overallScore
    ) {
    }

    /**
     * Bestleistungs-Kurven: {@code current} ist die Fahrt selbst, {@code previous} bis zu 3 vorherige
     * (neueste zuerst), {@code ftpWatts} die FTP fuer die Referenzlinie (kann null sein).
     */
    public record PowerCurveSet(PowerCurve current, List<PowerCurve> previous, Integer ftpWatts) {
    }

    public record PowerCurve(String activityId, String activityName, LocalDateTime activityDate,
                             List<PowerCurvePoint> points) {
    }

    public record PowerCurvePoint(int durationSeconds, Integer watts) {
    }
}
