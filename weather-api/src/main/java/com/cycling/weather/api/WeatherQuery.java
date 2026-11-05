package com.cycling.weather.api;

import java.time.Instant;
import java.util.List;

/**
 * Anfrage an den weather-service: Orte mit Zeitpunkt. Welche Punkte (z. B. alle 10 Minuten einer Fahrt) waehlt
 * der Aufrufer.
 *
 * @param points hoechstens {@link WeatherApi#MAX_POINTS}
 */
public record WeatherQuery(List<Point> points) {

    /**
     * @param lat  Breitengrad in Grad
     * @param lng  Laengengrad in Grad
     * @param time Zeitpunkt (im JSON ISO-8601 in UTC, z. B. "2026-11-01T07:30:00Z")
     */
    public record Point(double lat, double lng, Instant time) {
    }
}
