package com.cycling.report.weather;

import java.time.Instant;

/**
 * Ein Punkt der Fahrt, fuer den das Wetter abgefragt wird.
 *
 * @param minuteOfRide Minuten seit dem Start
 * @param distanceKm   Strecke bis hier; null ohne Distanz-Stream
 * @param time         Zeitpunkt in UTC
 * @param courseDeg    Fahrtrichtung (0 = Nord, 90 = Ost) ueber rund 500 m um den Punkt; null, wenn unklar
 *                     (Stillstand, zu kurze Strecke)
 */
public record WeatherSample(int minuteOfRide, Double distanceKm, double lat, double lng, Instant time,
                            Double courseDeg) {
}
