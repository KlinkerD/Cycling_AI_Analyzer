package com.cycling.report.api;

import com.cycling.web.ApiNonNull;

/**
 * Wetter an einem Punkt der Fahrt (alle 10 Minuten), Modelldaten von Open-Meteo. Nur bei Outdoor-Fahrten mit GPS;
 * einzelne Werte koennen null sein (Modell ohne Wert).
 *
 * @param minuteOfRide           Minuten seit dem Start (0, 10, 20, ...; der letzte Punkt ist das Ende der Fahrt)
 * @param distanceKm             zurueckgelegte Strecke bis hier; null ohne Distanz-Stream
 * @param time                   Zeitpunkt in UTC, z. B. "2026-11-01T07:40:00Z"
 * @param windDirectionDeg       Richtung, AUS der der Wind kommt (0 = Nord, 90 = Ost)
 * @param precipitationMmPerHour Niederschlagsintensitaet in mm/h
 */
public record WeatherPointDto(
        int minuteOfRide,
        Double distanceKm,
        double lat,
        double lng,
        @ApiNonNull String time,
        Double temperatureC,
        Double windSpeedKmh,
        Double windGustKmh,
        Double windDirectionDeg,
        Double precipitationMmPerHour
) {}
