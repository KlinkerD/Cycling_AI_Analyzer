package com.cycling.service;

import org.springframework.http.HttpStatus;

/**
 * Der intervals-service konnte die Daten nicht liefern (er selbst nicht erreichbar oder intervals.icu
 * mit Fehler). Wird vom {@code ApiExceptionHandler} als 502/503/404 an das Frontend gegeben.
 *
 * @see com.cycling.intervals.api.IntervalsErrorCode
 */
public class IntervalsUnavailableException extends RuntimeException {

    /** Code, wenn der intervals-service selbst nicht erreichbar ist. */
    public static final String SERVICE_UNREACHABLE = "INTERVALS_SERVICE_UNREACHABLE";

    private final HttpStatus status;
    private final String code;
    private final Long retryAfterSeconds;

    public IntervalsUnavailableException(HttpStatus status, String code, String message, Long retryAfterSeconds,
                                         Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /** 404, 503 oder 502 (alles andere). */
    public HttpStatus status() {
        return status;
    }

    /** Fehlercode des intervals-service, z. B. INTERVALS_AUTH, oder {@link #SERVICE_UNREACHABLE}. */
    public String code() {
        return code;
    }

    /** Retry-After in Sekunden; null, wenn nicht angegeben. */
    public Long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
