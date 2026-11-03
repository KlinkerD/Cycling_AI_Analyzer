package com.cycling.recommendation;

/** Der intervals-service liefert gerade keine Daten (nicht erreichbar oder Fehlerantwort). */
public class IntervalsUnavailableException extends RuntimeException {

    public IntervalsUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
