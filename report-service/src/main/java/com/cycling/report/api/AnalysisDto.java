package com.cycling.report.api;

import com.cycling.report.analysis.ActivityAnalysis;
import com.cycling.web.ApiNonNull;

import java.time.LocalDateTime;

/**
 * Kennzahlen und KI-Bewertung einer analysierten Fahrt, wie sie die Report-Seite anzeigt.
 *
 * @param intensityFactor NP / FTP
 * @param trainingLoad    TSS (1 h an der FTP = 100)
 * @param indoor          Indoor-Fahrt; null bei Analysen aus alter Version
 * @param ftpUsed         FTP in Watt, mit der IF/TSS berechnet wurden
 */
public record AnalysisDto(
        @ApiNonNull String activityId,
        String activityName,
        LocalDateTime activityDate,
        Double distanceKm,
        Integer durationSeconds,
        Double avgPowerWatts,
        Double maxPowerWatts,
        Double avgHeartRate,
        Double maxHeartRate,
        Double avgSpeedKmh,
        Double elevationGainM,
        Double avgCadenceRpm,
        Double normalizedPowerWatts,
        Double intensityFactor,
        Double trainingLoad,
        Boolean indoor,
        Integer ftpUsed,
        Integer overallScore,
        String strengths,
        String anomalies,
        String progress,
        String summary,
        LocalDateTime analyzedAt
) {

    public static AnalysisDto from(ActivityAnalysis a) {
        return new AnalysisDto(a.getActivityId(), a.getActivityName(), a.getActivityDate(), a.getDistanceKm(),
                a.getDurationSeconds(), a.getAvgPowerWatts(), a.getMaxPowerWatts(), a.getAvgHeartRate(),
                a.getMaxHeartRate(), a.getAvgSpeedKmh(), a.getElevationGainM(), a.getAvgCadenceRpm(),
                a.getNormalizedPowerWatts(), a.getIntensityFactor(), a.getTrainingLoad(), a.getIndoor(),
                a.getFtpUsed(), a.getOverallScore(), a.getStrengths(), a.getAnomalies(), a.getProgress(),
                a.getSummary(), a.getAnalyzedAt());
    }
}
