package com.cycling.messaging.contract;

/**
 * Antwort des ai-service auf einen {@link AnalysisRequest}. Bei {@code success=false}
 * steht der Grund in {@code error}, die Textfelder sind dann leer.
 */
public record AnalysisResult(
        String requestId,
        boolean success,
        String error,
        int score,
        String strengths,
        String anomalies,
        String progress,
        String summary
) {
    public static AnalysisResult failed(String requestId, String error) {
        return new AnalysisResult(requestId, false, error, 0, null, null, null, null);
    }
}
