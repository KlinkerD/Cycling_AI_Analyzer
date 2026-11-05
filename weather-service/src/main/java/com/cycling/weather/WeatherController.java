package com.cycling.weather;

import com.cycling.weather.api.WeatherApi;
import com.cycling.weather.api.WeatherQuery;
import com.cycling.weather.api.WeatherResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** REST-API fuer den report-service (Vertrag siehe {@link WeatherApi}). Fehler als ProblemDetail. */
@RestController
class WeatherController {

    private static final Logger log = LoggerFactory.getLogger(WeatherController.class);

    private final WeatherService weather;

    WeatherController(WeatherService weather) {
        this.weather = weather;
    }

    @PostMapping(WeatherApi.POINTS)
    WeatherResult points(@RequestBody WeatherQuery query) {
        return weather.points(query);
    }

    @ExceptionHandler(InvalidQueryException.class)
    ResponseEntity<ProblemDetail> invalid(InvalidQueryException e) {
        return problem(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> unreadable(HttpMessageNotReadableException e) {
        return problem(HttpStatus.BAD_REQUEST,
                "Anfrage ist kein gueltiges WeatherQuery-JSON (Zeiten als ISO-8601 in UTC, z. B. 2026-11-01T07:30:00Z)");
    }

    @ExceptionHandler(WeatherSourceException.class)
    ResponseEntity<ProblemDetail> source(WeatherSourceException e) {
        log.warn("Wetter nicht ladbar: {}", e.getMessage());
        return problem(e.status(), e.getMessage());
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
