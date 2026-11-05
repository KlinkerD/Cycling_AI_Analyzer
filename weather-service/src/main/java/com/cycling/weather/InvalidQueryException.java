package com.cycling.weather;

/** Ungueltige Anfrage (400). */
class InvalidQueryException extends RuntimeException {

    InvalidQueryException(String message) {
        super(message);
    }
}
