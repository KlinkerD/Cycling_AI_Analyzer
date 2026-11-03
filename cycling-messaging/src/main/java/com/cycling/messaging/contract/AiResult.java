package com.cycling.messaging.contract;

/**
 * Antwort des ai-service auf einen {@link AiRequest} (auf {@code Topics.aiResult(quelle)}).
 * Jeder Auftrag bekommt genau eine Antwort. Bei {@code success=false} steht der Grund in
 * {@code error}, {@code text} ist dann null.
 *
 * @param text Antworttext des Modells, unveraendert (bei {@code json=true} das JSON als Text)
 */
public record AiResult(String requestId, boolean success, String error, String text) {

    public static AiResult ok(String requestId, String text) {
        return new AiResult(requestId, true, null, text);
    }

    public static AiResult failed(String requestId, String error) {
        return new AiResult(requestId, false, error, null);
    }
}
