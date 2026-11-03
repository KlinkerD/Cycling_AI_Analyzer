package com.cycling.intervals;

import java.time.Duration;

/**
 * Zugang zu intervals.icu und Grenzen fuer die Aufrufe.
 *
 * @param baseUrl       z. B. https://intervals.icu/api/v1
 * @param maxRetries    Wiederholungen bei 429, 5xx und Netzwerkfehlern
 * @param maxRetryAfter laengste Wartezeit, die ein Retry-After von intervals.icu ausloesen darf
 * @param maxConcurrent hoechstens so viele Anfragen gleichzeitig an intervals.icu
 */
public record IntervalsProperties(String apiKey, String athleteId, String baseUrl,
                                  int maxRetries, Duration maxRetryAfter, int maxConcurrent) {

    /** Ohne API-Key und Athlete-ID startet der Service, beantwortet aber jede Anfrage mit einem Fehler. */
    public boolean isConfigured() {
        return isSet(apiKey) && isSet(athleteId);
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank() && !value.startsWith("HIER_") && !value.equals("xx");
    }
}
