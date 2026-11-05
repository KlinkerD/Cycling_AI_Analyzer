package com.cycling.report;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Inhalt eines Reports, wie ihn der Versand (Nachricht und Charts) braucht. Wird aus der gespeicherten
 * Analyse gebaut (siehe {@code ReportContentMapper}).
 *
 * @param analysis      Kennzahlen und KI-Texte der Fahrt
 * @param streams       Zeitreihen in voller Aufloesung (der report-service duennt sie fuer die Charts aus)
 * @param powerZones    Zeit in den Leistungszonen; leer = Chart entfaellt
 * @param hrZones       Zeit in den Herzfrequenzzonen; leer = Chart entfaellt
 * @param trend         die zuletzt analysierten Fahrten (neueste zuerst)
 * @param powerCurveSet Bestleistungen der Fahrt und der vorherigen; null = Chart entfaellt
 * @param powerZoneBands Leistungszonen in Watt (FTP der Fahrt) zum Einfaerben der Karte; leer = nach Tempo
 * @param hrZoneBands    Herzfrequenzzonen in bpm
 */
public record ReportContent(
        Analysis analysis,
        Streams streams,
        List<Zone> powerZones,
        List<Zone> hrZones,
        List<TrendPoint> trend,
        PowerCurveSet powerCurveSet,
        List<ZoneBand> powerZoneBands,
        List<ZoneBand> hrZoneBands
) {

    public ReportContent {
        powerZoneBands = powerZoneBands == null ? List.of() : List.copyOf(powerZoneBands);
        hrZoneBands = hrZoneBands == null ? List.of() : List.copyOf(hrZoneBands);
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
            List<Integer> cadence,   // rpm
            List<Double> lat,        // Grad; leer ohne GPS
            List<Double> lng         // Grad
    ) {
    }

    /** Grenzen einer Zone in Watt bzw. bpm; {@code upper} null = nach oben offen. */
    public record ZoneBand(String name, int lower, Integer upper, String color) {
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
