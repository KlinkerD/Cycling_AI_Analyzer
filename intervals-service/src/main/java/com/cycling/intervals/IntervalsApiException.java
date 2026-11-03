package com.cycling.intervals;

import com.cycling.intervals.api.IntervalsErrorCode;

import java.time.Duration;

/** Fehler beim Abruf von intervals.icu; wird als ProblemDetail mit {@link #code()} beantwortet. */
public class IntervalsApiException extends RuntimeException {

    private final IntervalsErrorCode code;
    private final Duration retryAfter;

    public IntervalsApiException(IntervalsErrorCode code, String message) {
        this(code, message, null, null);
    }

    public IntervalsApiException(IntervalsErrorCode code, String message, Duration retryAfter, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.retryAfter = retryAfter;
    }

    public IntervalsErrorCode code() {
        return code;
    }

    /** Nur bei {@link IntervalsErrorCode#INTERVALS_RATE_LIMIT}: wann ein neuer Versuch sinnvoll ist; sonst null. */
    public Duration retryAfter() {
        return retryAfter;
    }
}
