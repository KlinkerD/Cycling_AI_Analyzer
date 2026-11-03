package com.cycling.config;

import com.cycling.service.IntervalsUnavailableException;
import com.cycling.service.RecommendationUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Fehler des intervals-service als ProblemDetail (502/503/404) statt 500. {@code detail} ist ein
 * verstaendlicher Text, {@code code} der Fehlercode (z. B. INTERVALS_AUTH). Fehlt die Tagesempfehlung
 * (recommendation-service antwortet nicht), gibt es 503 mit {@code code} RECOMMENDATION_UNAVAILABLE.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

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

    @ExceptionHandler(RecommendationUnavailableException.class)
    public ResponseEntity<ProblemDetail> recommendationUnavailable(RecommendationUnavailableException e) {
        log.warn("Tagesempfehlung nicht verfuegbar: {}", e.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        problem.setTitle("Tagesempfehlung nicht verfügbar");
        problem.setProperty("code", "RECOMMENDATION_UNAVAILABLE");
        // Das Frontend zeigt err.error.message in der Snackbar an.
        problem.setProperty("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
    }
}
