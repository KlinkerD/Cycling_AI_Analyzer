package com.cycling.weather.api;

import java.time.Instant;
import java.util.List;

/**
 * Antwort des weather-service: zu jedem angefragten Punkt (gleiche Reihenfolge) die Modellwerte, zeitlich
 * interpoliert. Ein Wert ist null, wenn das Modell fuer diesen Ort/Zeitpunkt nichts liefert.
 *
 * @param source Datenquelle, z. B. "open-meteo"
 */
public record WeatherResult(List<Point> points, String source) {

    /**
     * @param lat                    Breitengrad in Grad (wie angefragt)
     * @param lng                    Laengengrad in Grad (wie angefragt)
     * @param time                   Zeitpunkt in UTC (wie angefragt)
     * @param temperatureC           Lufttemperatur in 2 m Hoehe, Grad Celsius
     * @param windSpeedKmh           mittlerer Wind in 10 m Hoehe, km/h
     * @param windGustKmh            Boeen in 10 m Hoehe, km/h
     * @param windDirectionDeg       Richtung, AUS der der Wind kommt (0 = Nord, 90 = Ost), Grad
     * @param precipitationMmPerHour Niederschlagsintensitaet in mm/h (Summe = Intensitaet mal Dauer)
     */
    public record Point(
            double lat,
            double lng,
            Instant time,
            Double temperatureC,
            Double windSpeedKmh,
            Double windGustKmh,
            Double windDirectionDeg,
            Double precipitationMmPerHour
    ) {
    }
}
