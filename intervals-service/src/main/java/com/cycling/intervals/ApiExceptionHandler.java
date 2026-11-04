package com.cycling.intervals;

import com.cycling.intervals.api.IntervalsApi;
import com.cycling.intervals.api.IntervalsErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Fehler als ProblemDetail mit Feld {@code code} (siehe {@link IntervalsErrorCode}). */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(IntervalsApiException.class)
    public ResponseEntity<ProblemDetail> intervalsError(IntervalsApiException e) {
        HttpStatus status = statusFor(e.code());
        if (status.is5xxServerError()) {
            log.warn("intervals.icu-Abruf fehlgeschlagen ({}): {}", e.code(), e.getMessage());
        }
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status);
        if (e.retryAfter() != null) {
            response.header(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, e.retryAfter().toSeconds())));
        }
        return response.body(problem(status, e.code(), e.getMessage()));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<ProblemDetail> badRequest(Exception e) {
        return ResponseEntity.badRequest()
                .body(problem(HttpStatus.BAD_REQUEST, IntervalsErrorCode.BAD_REQUEST, e.getMessage()));
    }

    static HttpStatus statusFor(IntervalsErrorCode code) {
        return switch (code) {
            case INTERVALS_AUTH, INTERVALS_UNAVAILABLE -> HttpStatus.BAD_GATEWAY;
            case INTERVALS_RATE_LIMIT, INTERVALS_NOT_CONFIGURED -> HttpStatus.SERVICE_UNAVAILABLE;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
        };
    }

    private static ProblemDetail problem(HttpStatus status, IntervalsErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty(IntervalsApi.PROBLEM_CODE, code.name());
        return problem;
    }
}
