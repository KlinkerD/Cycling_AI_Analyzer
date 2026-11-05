package com.cycling.report.weather;

import com.cycling.report.api.StreamDataDto;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WeatherEligibilityTest {

    private static StreamDataDto gps(List<Double> lat, List<Double> lng) {
        return new StreamDataDto(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), lat, lng);
    }

    private static final StreamDataDto GPS = gps(List.of(48.0, 48.1), List.of(11.5, 11.6));

    @Test
    void outdoorRideWithGps() {
        assertThat(WeatherEligibility.isOutdoorWithGps(false, "Ride", GPS)).isTrue();
    }

    @Test
    void otherOutdoorRideTypes() {
        assertThat(WeatherEligibility.isOutdoorWithGps(false, "GravelRide", GPS)).isTrue();
        assertThat(WeatherEligibility.isOutdoorWithGps(false, "MountainBikeRide", GPS)).isTrue();
        assertThat(WeatherEligibility.isOutdoorWithGps(false, "EBikeRide", GPS)).isTrue();
        assertThat(WeatherEligibility.isOutdoorWithGps(false, "Run", GPS)).isFalse();
    }

    @Test
    void indoorOrUnknownIsNot() {
        assertThat(WeatherEligibility.isOutdoorWithGps(true, "Ride", GPS)).isFalse();
        assertThat(WeatherEligibility.isOutdoorWithGps(null, "Ride", GPS)).isFalse();
    }

    @Test
    void virtualRideIsNotEvenWithGps() {
        // Zwift: Fantasie-Koordinaten, auch wenn die Indoor-Kennung faelschlich false waere
        assertThat(WeatherEligibility.isOutdoorWithGps(false, "VirtualRide", GPS)).isFalse();
        assertThat(WeatherEligibility.isOutdoorWithGps(false, null, GPS)).isFalse();
    }

    @Test
    void needsTwoValidGpsPoints() {
        assertThat(WeatherEligibility.isOutdoorWithGps(false, "Ride", gps(List.of(), List.of()))).isFalse();
        assertThat(WeatherEligibility.isOutdoorWithGps(false, "Ride",
                gps(Arrays.asList(48.0, null, null), Arrays.asList(11.5, 11.6, null)))).isFalse();
        assertThat(WeatherEligibility.isOutdoorWithGps(false, "Ride", null)).isFalse();
    }
}
