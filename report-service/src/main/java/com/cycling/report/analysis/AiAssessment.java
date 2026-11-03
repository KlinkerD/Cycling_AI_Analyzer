package com.cycling.report.analysis;

/** KI-Bewertung einer Fahrt, aus der JSON-Antwort des ai-service gelesen (siehe {@link AnalysisPrompt#parse}). */
public record AiAssessment(int score, String strengths, String anomalies, String progress, String summary) {
}
