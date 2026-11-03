package com.cycling.intervals.api;

/** Wert des Felds {@code code} in den ProblemDetail-Fehlerantworten des intervals-service. */
public enum IntervalsErrorCode {
    /** intervals.icu lehnt API-Key/Athlete-ID ab (401/403) -> HTTP 502. */
    INTERVALS_AUTH,
    /** Aktivitaet o. ae. gibt es bei intervals.icu nicht -> HTTP 404. */
    NOT_FOUND,
    /** Rate-Limit von intervals.icu auch nach Wiederholungen -> HTTP 503 mit Retry-After. */
    INTERVALS_RATE_LIMIT,
    /** intervals.icu antwortet mit 5xx, Timeout oder Netzwerkfehler -> HTTP 502. */
    INTERVALS_UNAVAILABLE,
    /** INTERVALS_API_KEY / INTERVALS_ATHLETE_ID fehlen -> HTTP 503. */
    INTERVALS_NOT_CONFIGURED,
    /** Ungueltige Anfrage an den intervals-service selbst (z. B. Datumsformat) -> HTTP 400. */
    BAD_REQUEST
}
