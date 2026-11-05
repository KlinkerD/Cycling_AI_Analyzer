package com.cycling.weather.api;

/**
 * REST-Vertrag des weather-service.
 *
 * <p>{@code POST} {@link #POINTS} mit {@link WeatherQuery} liefert {@link WeatherResult}: zu jedem Punkt (Ort und
 * Zeitpunkt in UTC) die Modellwerte von Open-Meteo. Fehler kommen als ProblemDetail: 400 bei ungueltigen Punkten,
 * 503 wenn Open-Meteo nicht antwortet (auch nach einer Wiederholung bei 429), 502 bei unlesbaren Antworten.
 */
public final class WeatherApi {

    public static final String POINTS = "/api/weather/points";

    /** Hoechstens so viele Punkte pro Anfrage (eine 8-h-Fahrt alle 10 min sind ~50). */
    public static final int MAX_POINTS = 500;

    private WeatherApi() {
    }
}
