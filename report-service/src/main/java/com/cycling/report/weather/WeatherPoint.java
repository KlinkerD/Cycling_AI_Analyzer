package com.cycling.report.weather;

import com.cycling.types.ActivityId;

import com.cycling.report.api.WeatherPointDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Gespeichertes Wetter einer analysierten Fahrt, ein Eintrag pro Punkt (alle 10 Minuten). Wird einmal bei der
 * Analyse geholt, damit Report und KI-Bewertung dieselben Werte zeigen; eine Neuanalyse ersetzt alle Punkte.
 * Verknuepft ueber die Aktivitaets-ID, nicht ueber eine JPA-Beziehung.
 */
@Entity
@Table(name = "weather_point", indexes = @Index(name = "idx_weather_activity", columnList = "activityId"))
public class WeatherPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private ActivityId activityId;

    private int minuteOfRide;
    private Double distanceKm;
    private double lat;
    private double lng;
    @Column(nullable = false)
    private Instant time;
    private Double temperatureC;
    private Double windSpeedKmh;
    private Double windGustKmh;
    private Double windDirectionDeg;
    private Double precipitationMmPerHour;

    protected WeatherPoint() {
    }

    static WeatherPoint of(ActivityId activityId, WeatherPointDto p) {
        WeatherPoint w = new WeatherPoint();
        w.activityId = activityId;
        w.minuteOfRide = p.minuteOfRide();
        w.distanceKm = p.distanceKm();
        w.lat = p.lat();
        w.lng = p.lng();
        w.time = p.time();
        w.temperatureC = p.temperatureC();
        w.windSpeedKmh = p.windSpeedKmh();
        w.windGustKmh = p.windGustKmh();
        w.windDirectionDeg = p.windDirectionDeg();
        w.precipitationMmPerHour = p.precipitationMmPerHour();
        return w;
    }

    WeatherPointDto toDto() {
        return new WeatherPointDto(minuteOfRide, distanceKm, lat, lng, time, temperatureC, windSpeedKmh, windGustKmh,
                windDirectionDeg, precipitationMmPerHour);
    }

    public ActivityId getActivityId() {
        return activityId;
    }

    public int getMinuteOfRide() {
        return minuteOfRide;
    }
}
