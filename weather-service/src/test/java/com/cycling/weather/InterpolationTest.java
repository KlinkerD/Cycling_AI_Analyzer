package com.cycling.weather;

import com.cycling.weather.api.WeatherResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InterpolationTest {

    /** 15-min-Reihe ab 07:00 UTC (1_700_000_000 ist beliebig, Abstand 900 s). */
    private static final long T0 = 1_700_000_000L;

    private static Series series(List<Double> temp, List<Double> dir, List<Double> precip) {
        return new Series(15, List.of(T0, T0 + 900, T0 + 1800),
                temp, List.of(10.0, 20.0, 30.0), List.of(20.0, 30.0, 40.0), dir, precip);
    }

    private static final Series SERIES = series(List.of(6.0, 8.0, 7.0), List.of(200.0, 220.0, 240.0),
            List.of(0.0, 0.5, 0.25));

    private static WeatherResult.Point at(Series s, long t) {
        return Interpolation.at(s, 48.1, 11.5, Instant.ofEpochSecond(t));
    }

    @Test
    void interpolatesLinearlyBetweenSamples() {
        WeatherResult.Point p = at(SERIES, T0 + 600); // 10 min, 2/3 zwischen 07:00 und 07:15

        assertThat(p.temperatureC()).isEqualTo(7.3);
        assertThat(p.windSpeedKmh()).isEqualTo(16.7);
        assertThat(p.windGustKmh()).isEqualTo(26.7);
        assertThat(p.windDirectionDeg()).isEqualTo(213.0);
    }

    @Test
    void exactSampleTakesItsValue() {
        WeatherResult.Point p = at(SERIES, T0 + 1800);

        assertThat(p.temperatureC()).isEqualTo(7.0);
        assertThat(p.windDirectionDeg()).isEqualTo(240.0);
    }

    @Test
    void precipitationIsRateOfContainingInterval() {
        // 07:10 liegt im Intervall (07:00, 07:15], dessen Summe am 07:15-Wert steht: 0,5 mm in 15 min = 2 mm/h
        assertThat(at(SERIES, T0 + 600).precipitationMmPerHour()).isEqualTo(2.0);
        // genau 07:15 zaehlt zum endenden Intervall
        assertThat(at(SERIES, T0 + 900).precipitationMmPerHour()).isEqualTo(2.0);
        assertThat(at(SERIES, T0 + 1000).precipitationMmPerHour()).isEqualTo(1.0);
    }

    @Test
    void windDirectionWrapsAroundNorth() {
        Series s = series(List.of(5.0, 5.0, 5.0), List.of(350.0, 10.0, 10.0), List.of(0.0, 0.0, 0.0));

        assertThat(at(s, T0 + 450).windDirectionDeg()).isEqualTo(0.0);
    }

    @Test
    void outsideSeriesGivesNulls() {
        WeatherResult.Point p = at(SERIES, T0 - 1);

        assertThat(p.temperatureC()).isNull();
        assertThat(p.windSpeedKmh()).isNull();
        assertThat(p.precipitationMmPerHour()).isNull();
        assertThat(at(Series.empty(15), T0).temperatureC()).isNull();
    }

    @Test
    void missingNeighbourFallsBackToNearer() {
        Series s = series(Arrays.asList(6.0, null, 7.0), List.of(200.0, 220.0, 240.0), List.of(0.0, 0.0, 0.0));

        assertThat(at(s, T0 + 100).temperatureC()).isEqualTo(6.0);
        assertThat(at(s, T0 + 800).temperatureC()).isNull();
    }

    @Test
    void keepsRequestedPointData() {
        WeatherResult.Point p = Interpolation.at(SERIES, 48.123, 11.456, Instant.ofEpochSecond(T0));

        assertThat(p.lat()).isEqualTo(48.123);
        assertThat(p.lng()).isEqualTo(11.456);
        assertThat(p.time()).isEqualTo(Instant.ofEpochSecond(T0));
    }
}
