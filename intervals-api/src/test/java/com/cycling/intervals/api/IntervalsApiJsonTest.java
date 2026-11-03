package com.cycling.intervals.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Die Records muessen JSON-Roundtrips ueberstehen, weil intervals-service und backend sie austauschen. */
class IntervalsApiJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private <T> T roundTrip(T value, Class<T> type) throws Exception {
        return mapper.readValue(mapper.writeValueAsString(value), type);
    }

    @Test
    void activitySurvivesRoundTrip() throws Exception {
        IntervalsActivity activity = new IntervalsActivity("i1", "Runde", "2026-11-01T08:30:00", "Ride",
                42000.0, 5400, 5600, 180.0, 195.0, 600.0, 140.0, 175.0, 7.8, 350.0);

        assertThat(roundTrip(activity, IntervalsActivity.class)).isEqualTo(activity);
    }

    @Test
    void athleteZonesSurviveRoundTrip() throws Exception {
        IntervalsAthleteZones zones = new IntervalsAthleteZones(
                new IntervalsAthleteThresholds("Dan", 250, 235, 170, 190, 72.5),
                List.of(new IntervalsZone(1, "Recovery", 0.0, 0.55, 0, 138, "#9e9e9e")),
                List.of(new IntervalsZone(1, "Z1", 0.0, 0.8, 0, 136, "#9e9e9e")),
                "lthr");

        assertThat(roundTrip(zones, IntervalsAthleteZones.class)).isEqualTo(zones);
    }

    @Test
    void activityZonesAndPowerCurveSurviveRoundTrip() throws Exception {
        IntervalsActivityZones zones = new IntervalsActivityZones(
                List.of(new IntervalsZoneShare("Z2", "Endurance", 1800, 55.5, "#42a5f5")), List.of(), true, 235);
        IntervalsPowerCurve curve = new IntervalsPowerCurve(List.of(new IntervalsPowerCurve.Point(5, 600)));

        assertThat(roundTrip(zones, IntervalsActivityZones.class)).isEqualTo(zones);
        assertThat(roundTrip(curve, IntervalsPowerCurve.class)).isEqualTo(curve);
    }

    @Test
    void wellnessAndStreamsSurviveRoundTrip() throws Exception {
        IntervalsWellnessPoint point = new IntervalsWellnessPoint("2026-11-01", 55.2, 60.1, -4.9, 3.4);
        IntervalsStreams streams = new IntervalsStreams(List.of(0.0, 10.0), Arrays.asList(150, null),
                List.of(120, 125), List.of(8.0, 8.1), List.of(100.0, 101.0), List.of(0, 1), List.of());

        assertThat(roundTrip(point, IntervalsWellnessPoint.class)).isEqualTo(point);
        assertThat(roundTrip(streams, IntervalsStreams.class)).isEqualTo(streams);
    }
}
