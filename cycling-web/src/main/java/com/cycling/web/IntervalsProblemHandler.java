package com.cycling.web;

import com.cycling.intervals.client.IntervalsUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Fehler des intervals-service als ProblemDetail (502/503/404) statt 500. {@code detail} ist ein
 * verstaendlicher Text, {@code code} der Fehlercode (z. B. INTERVALS_AUTH).
 */
@RestControllerAdvice
public class IntervalsProblemHandler {

    private static final Logger log = LoggerFactory.getLogger(IntervalsProblemHandler.class);

    @ExceptionHandler(IntervalsUnavailableException.class)
    public ResponseEntity<ProblemDetail> intervalsUnavailable(IntervalsUnavailableException e) {
        log.warn("intervals-Daten nicht verfuegbar ({}): {}", e.code(), e.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
        problem.setTitle("intervals.icu-Daten nicht verfügbar");
        problem.setProperty("code", e.code());
        ResponseEntity.BodyBuilder response = ResponseEntity.status(e.status());
        if (e.retryAfterSeconds() != null) {
            response.header(HttpHeaders.RETRY_AFTER, String.valueOf(e.retryAfterSeconds()));
        }
        return response.body(problem);
    }
}
