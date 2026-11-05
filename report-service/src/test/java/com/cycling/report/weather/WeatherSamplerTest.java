package com.cycling.report.weather;

import com.cycling.report.api.StreamDataDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class WeatherSamplerTest {

    private static final Instant START = Instant.parse("2026-11-01T07:30:00Z");
    /** ~1 m Breitengrad in Grad. */
    private static final double DEG_PER_M = 1 / 111_195.0;

    /** Fahrt genau nach Norden, 1 Probe pro Sekunde, 5 m/s; {@code gpsGap} = Sekunden ohne GPS (von, bis). */
    private static StreamDataDto northbound(int seconds, int gapFrom, int gapTo) {
        List<Integer> time = new ArrayList<>();
        List<Double> distance = new ArrayList<>();
        List<Double> lat = new ArrayList<>();
        List<Double> lng = new ArrayList<>();
        for (int t = 0; t <= seconds; t++) {
            time.add(t);
            distance.add(t * 5.0);
            boolean gap = t >= gapFrom && t <= gapTo;
            lat.add(gap ? null : 48.0 + t * 5.0 * DEG_PER_M);
            lng.add(gap ? null : 11.5);
        }
        return new StreamDataDto(distance, List.of(), List.of(), List.of(), List.of(), time, List.of(), lat, lng);
    }

    @Test
    void everyTenMinutesPlusEnd() {
        List<WeatherSample> samples = WeatherSampler.sample(northbound(1500, -1, -1), START);

        assertThat(samples).extracting(WeatherSample::minuteOfRide).containsExactly(0, 10, 20, 25);
        assertThat(samples.get(1).time()).isEqualTo(Instant.parse("2026-11-01T07:40:00Z"));
        assertThat(samples.get(1).distanceKm()).isEqualTo(3.0);
        assertThat(samples.get(1).lat()).isCloseTo(48.0 + 3000 * DEG_PER_M, within(1e-9));
    }

    @Test
    void endTooCloseToLastTickIsDropped() {
        assertThat(WeatherSampler.sample(northbound(1230, -1, -1), START))
                .extracting(WeatherSample::minuteOfRide).containsExactly(0, 10, 20);
    }

    @Test
    void shortRideHasStartAndEnd() {
        assertThat(WeatherSampler.sample(northbound(300, -1, -1), START))
                .extracting(WeatherSample::minuteOfRide).containsExactly(0, 5);
    }

    @Test
    void gpsGapUsesNearestPositionWithinTwoMinutes() {
        // ohne GPS von 550 bis 640 s: naechste Position zu 600 s ist 549 s (51 s) bzw. 641 s (41 s)
        WeatherSample s = WeatherSampler.sample(northbound(900, 550, 640), START).get(1);

        assertThat(s.minuteOfRide()).isEqualTo(10);
        assertThat(s.distanceKm()).isEqualTo(3.21);
        assertThat(s.time()).isEqualTo(Instant.parse("2026-11-01T07:40:00Z"));
    }

    @Test
    void longGpsGapDropsPoint() {
        assertThat(WeatherSampler.sample(northbound(1300, 450, 750), START))
                .extracting(WeatherSample::minuteOfRide).containsExactly(0, 20, 21);
    }

    @Test
    void courseFollowsDirectionOfTravel() {
        List<WeatherSample> samples = WeatherSampler.sample(northbound(1300, -1, -1), START);

        assertThat(samples).allSatisfy(s -> assertThat(s.courseDeg()).isCloseTo(0.0, within(0.5)));
    }

    @Test
    void standingStillHasNoCourse() {
        List<Integer> time = List.of(0, 1, 2, 700);
        List<Double> same = List.of(48.0, 48.0, 48.0, 48.0);
        StreamDataDto s = new StreamDataDto(List.of(0.0, 0.0, 0.0, 10.0), List.of(), List.of(), List.of(), List.of(),
                time, List.of(), same, List.of(11.5, 11.5, 11.5, 11.5));

        assertThat(WeatherSampler.sample(s, START)).allSatisfy(w -> assertThat(w.courseDeg()).isNull());
    }

    @Test
    void bearingEast() {
        assertThat(WeatherSampler.bearing(48.0, 11.0, 48.0, 11.1)).isCloseTo(90.0, within(0.1));
        assertThat(WeatherSampler.bearing(48.0, 11.0, 47.9, 11.0)).isCloseTo(180.0, within(0.1));
    }

    @Test
    void withoutStartOrGpsNothing() {
        assertThat(WeatherSampler.sample(northbound(600, -1, -1), null)).isEmpty();
        StreamDataDto noGps = new StreamDataDto(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(0, 1),
                List.of(), List.of(), List.of());
        assertThat(WeatherSampler.sample(noGps, START)).isEmpty();
    }
}
