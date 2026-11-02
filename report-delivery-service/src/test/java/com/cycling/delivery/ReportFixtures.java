package com.cycling.delivery;

import com.cycling.delivery.report.PowerCurveSetDto;
import com.cycling.delivery.report.ReportDto;
import com.cycling.delivery.report.StreamDataDto;
import com.cycling.delivery.report.ActivityAnalysis;

import java.time.LocalDateTime;
import java.util.List;

/** Gemeinsame Test-Daten fuer die Versand-Tests. */
final class ReportFixtures {

    private ReportFixtures() {
    }

    static ActivityAnalysis analysis(String activityId) {
        ActivityAnalysis a = new ActivityAnalysis();
        a.setActivityId(activityId);
        a.setActivityName("Morgenrunde");
        a.setActivityDate(LocalDateTime.of(2026, 11, 1, 8, 30));
        a.setDistanceKm(42.5);
        a.setDurationSeconds(5400);
        a.setAvgPowerWatts(180.0);
        a.setOverallScore(8);
        a.setStrengths("Gleichmaessig getreten");
        a.setAnomalies("Puls am Ende hoch");
        a.setProgress("Mehr Leistung als letzte Woche");
        a.setSummary("Solide Einheit");
        a.setAnalyzedAt(LocalDateTime.of(2026, 11, 1, 10, 0));
        return a;
    }

    static StreamDataDto streams(List<Integer> cadence) {
        return new StreamDataDto(List.of(0.0, 100.0), List.of(150, 160), List.of(120, 130),
                List.of(8.0, 8.5), List.of(100.0, 101.0), List.of(0, 10), cadence);
    }

    static ReportDto report(String activityId) {
        return new ReportDto(analysis(activityId), streams(List.of(85, 90)), List.of(), List.of(), List.of(),
                new PowerCurveSetDto(null, List.of(), null));
    }

    static byte[] png(String marker) {
        return ("PNG-" + marker).getBytes();
    }
}
