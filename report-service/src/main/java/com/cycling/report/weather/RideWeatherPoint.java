package com.cycling.report.weather;

import com.cycling.report.api.WeatherPointDto;

/**
 * Wetter an einem Punkt der Fahrt, wie es bei der Analyse geholt wurde: der Punkt ({@link WeatherSample}, mit
 * Fahrtrichtung fuer den Prompt) plus die Modellwerte. Gespeichert und ausgeliefert wird {@link #toDto()} (ohne
 * Fahrtrichtung).
 */
public record RideWeatherPoint(
        WeatherSample sample,
        Double temperatureC,
        Double windSpeedKmh,
        Double windGustKmh,
        Double windDirectionDeg,
        Double precipitationMmPerHour
) {

    /** Gegen-, Rueckenwind usw.; null ohne Fahrtrichtung oder Windrichtung. */
    public WindRelation windRelation() {
        return WindRelation.of(sample.courseDeg(), windDirectionDeg);
    }

    public WeatherPointDto toDto() {
        return new WeatherPointDto(sample.minuteOfRide(), sample.distanceKm(), sample.lat(), sample.lng(),
                sample.time().toString(), temperatureC, windSpeedKmh, windGustKmh, windDirectionDeg,
                precipitationMmPerHour);
    }
}
