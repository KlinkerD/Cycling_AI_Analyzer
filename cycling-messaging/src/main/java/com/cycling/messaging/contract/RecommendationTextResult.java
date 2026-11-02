package com.cycling.messaging.contract;

/** Antwort auf einen {@link RecommendationTextRequest}; bei {@code success=false} bleibt der Fallback-Text. */
public record RecommendationTextResult(String requestId, boolean success, String error, String text) {

    public static RecommendationTextResult failed(String requestId, String error) {
        return new RecommendationTextResult(requestId, false, error, null);
    }
}
