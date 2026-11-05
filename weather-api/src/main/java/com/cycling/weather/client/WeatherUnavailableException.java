package com.cycling.weather.client;

/** Der weather-service konnte kein Wetter liefern (nicht erreichbar, Fehlerstatus oder leere Antwort). */
public class WeatherUnavailableException extends RuntimeException {

    public WeatherUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
