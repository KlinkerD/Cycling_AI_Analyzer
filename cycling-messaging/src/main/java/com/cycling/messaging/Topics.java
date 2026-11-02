package com.cycling.messaging;

/**
 * Alle MQTT-Topics an einer Stelle. Payload ist immer JSON (siehe {@link MessageCodec}),
 * QoS 1. Die zugehoerigen Nachrichten liegen in {@code com.cycling.messaging.contract}.
 */
public final class Topics {

    private static final String ROOT = "cycling/";

    /** analysis-service -> ai-service: Training analysieren ({@code AnalysisRequest}). */
    public static final String AI_ANALYSIS_REQUEST = ROOT + "ai/analysis/request";
    /** ai-service -> analysis-service: Ergebnis der Analyse ({@code AnalysisResult}). */
    public static final String AI_ANALYSIS_RESULT = ROOT + "ai/analysis/result";

    /** analysis-service -> ai-service: Text fuer die Tagesempfehlung ({@code RecommendationTextRequest}). */
    public static final String AI_RECOMMENDATION_REQUEST = ROOT + "ai/recommendation/request";
    /** ai-service -> analysis-service: Text der Tagesempfehlung ({@code RecommendationTextResult}). */
    public static final String AI_RECOMMENDATION_RESULT = ROOT + "ai/recommendation/result";

    private Topics() {
    }
}
