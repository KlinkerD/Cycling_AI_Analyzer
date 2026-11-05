package com.cycling.weather;

import org.springframework.http.HttpStatus;

/** Open-Meteo hat nicht (503) oder nicht lesbar (502) geantwortet. */
class WeatherSourceException extends RuntimeException {

    private final HttpStatus status;

    WeatherSourceException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    HttpStatus status() {
        return status;
    }
}
