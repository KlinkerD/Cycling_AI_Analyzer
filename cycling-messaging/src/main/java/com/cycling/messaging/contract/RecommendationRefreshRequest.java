package com.cycling.messaging.contract;

/**
 * backend -> recommendation-service: Tagesempfehlung sofort neu berechnen (Refresh-Button). Die Antwort
 * kommt als {@link RecommendationUpdated} mit derselben {@code requestId}.
 */
public record RecommendationRefreshRequest(String requestId) {
}
