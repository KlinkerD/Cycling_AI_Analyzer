package com.cycling.report;


import java.time.LocalDateTime;
import java.util.List;

/** Gemeinsame Test-Daten fuer die report-service-Tests. */
final class ReportFixtures {

    static final LocalDateTime ACTIVITY_DATE = LocalDateTime.of(2026, 11, 1, 8, 30);
    static final LocalDateTime ANALYZED_AT = LocalDateTime.of(2026, 11, 1, 10, 0);

    private ReportFixtures() {
    }

    static ReportContent.Analysis analysis(String activityId) {
        return analysis(activityId, 8, "Puls am Ende hoch", ACTIVITY_DATE, ANALYZED_AT);
    }

    /** Analyse mit den Werten, die einzelne Tests variieren. */
    static ReportContent.Analysis analysis(String activityId, Integer score, String anomalies,
                                           LocalDateTime activityDate, LocalDateTime analyzedAt) {
        return new ReportContent.Analysis(activityId, "Morgenrunde", activityDate, 42.5, 5400,
                180.0, null, null, null, null, null, null, null, score,
                "Gleichmaessig getreten", anomalies, "Mehr Leistung als letzte Woche", "Solide Einheit", analyzedAt);
    }

    static ReportContent.Streams streams(List<Integer> cadence) {
        return new ReportContent.Streams(List.of(0.0, 100.0), List.of(150, 160), List.of(120, 130),
                List.of(8.0, 8.5), List.of(100.0, 101.0), List.of(0, 10), cadence, List.of(53.55, 53.56),
                List.of(9.99, 10.0));
    }

    static ReportContent report(String activityId) {
        return report(analysis(activityId));
    }

    static ReportContent report(ReportContent.Analysis analysis) {
        return new ReportContent(analysis, streams(List.of(85, 90)), List.of(), List.of(), List.of(),
                new ReportContent.PowerCurveSet(null, List.of(), null), List.of(), List.of(), List.of());
    }

    static byte[] png(String marker) {
        return ("PNG-" + marker).getBytes();
    }
}
