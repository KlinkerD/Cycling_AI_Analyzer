package com.cycling.intervals.client;

import org.springframework.http.HttpStatus;

/**
 * Der intervals-service konnte die Daten nicht liefern (er selbst nicht erreichbar oder intervals.icu
 * mit Fehler). Die Web-Services geben das als ProblemDetail (502/503/404) an das Frontend weiter.
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

    /** Kurzform fuer "intervals-service nicht erreichbar" (503). */
    public IntervalsUnavailableException(String message, Throwable cause) {
        this(HttpStatus.SERVICE_UNAVAILABLE, SERVICE_UNREACHABLE, message, null, cause);
    }

    /** 400 (unplausible Eingabe), 404, 503 oder 502 (alles andere). */
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
