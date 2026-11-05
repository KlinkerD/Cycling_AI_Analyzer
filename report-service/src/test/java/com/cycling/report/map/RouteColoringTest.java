package com.cycling.report.map;

import com.cycling.report.ReportContent.Streams;
import com.cycling.report.ReportContent.ZoneBand;
import com.cycling.report.map.RouteColoring.ColoredRoute;
import com.cycling.report.map.RouteColoring.Point;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RouteColoringTest {

    private static final List<ZoneBand> BANDS = List.of(
            new ZoneBand("Recovery", 0, 150, "#9e9e9e"),
            new ZoneBand("Endurance", 150, 250, "#42a5f5"),
            new ZoneBand("Threshold", 250, null, "#ffa726"));

    private static Streams streams(List<Integer> watts, List<Double> velocity, List<Double> lat, List<Double> lng) {
        int n = lat.size();
        return new Streams(Collections.nCopies(n, 0.0), watts, List.of(), velocity, List.of(), List.of(), List.of(),
                lat, lng);
    }

    private static List<Double> line(int n, double start) {
        List<Double> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(start + i * 0.001);
        return out;
    }

    @Test
    void indoorRideWithoutGpsHasNoRoute() {
        assertThat(RouteColoring.color(streams(List.of(200), List.of(8.0), List.of(), List.of()), BANDS)).isNull();
    }

    @Test
    void gapsAndZeroCoordinatesAreSkipped() {
        Streams s = streams(List.of(), List.of(), Arrays.asList(53.5, null, 0.0, 53.6),
                Arrays.asList(10.0, null, 0.0, 10.1));

        ColoredRoute route = RouteColoring.color(s, BANDS);

        assertThat(route.points()).extracting(Point::lat).containsExactly(53.5, 53.6);
    }

    @Test
    void powerIsColoredByZonesOfTheRide() {
        // 100 Punkte bei 100 W, dann 100 Punkte bei 300 W: geglaettet bleiben die Raender in Z1 bzw. Z3
        List<Integer> watts = new ArrayList<>(Collections.nCopies(100, 100));
        watts.addAll(Collections.nCopies(100, 300));

        ColoredRoute route = RouteColoring.color(streams(watts, List.of(), line(200, 53.5), line(200, 10.0)), BANDS);

        assertThat(route.legendTitle()).isEqualTo("Leistungszonen");
        assertThat(route.points().getFirst().color()).isEqualTo(0);
        assertThat(route.points().getLast().color()).isEqualTo(2);
        assertThat(route.colors().get(2)).isEqualTo("#ffa726");
        assertThat(route.legend()).extracting(RouteColoring.LegendEntry::label)
                .containsExactly("Z1 Recovery (0–150 W)", "Z2 Endurance (150–250 W)", "Z3 Threshold (≥ 250 W)");
    }

    @Test
    void withoutPowerTheRouteIsColoredBySpeed() {
        List<Double> velocity = new ArrayList<>(Collections.nCopies(100, 5.0));
        velocity.addAll(Collections.nCopies(100, 12.0));

        ColoredRoute route = RouteColoring.color(streams(List.of(), velocity, line(200, 53.5), line(200, 10.0)), BANDS);

        assertThat(route.legendTitle()).isEqualTo("Tempo");
        assertThat(route.points().getFirst().color()).isEqualTo(0);
        assertThat(route.points().getLast().color()).isEqualTo(RouteColoring.SPEED_COLORS.length - 1);
    }

    @Test
    void wattsWithoutZonesFallBackToSpeed() {
        ColoredRoute route = RouteColoring.color(
                streams(List.of(200, 210), List.of(8.0, 9.0), line(2, 53.5), line(2, 10.0)), List.of());

        assertThat(route.legendTitle()).isEqualTo("Tempo");
    }

    @Test
    void zoneLabelKeepsNamesThatAlreadyCarryTheNumber() {
        assertThat(RouteColoring.zoneLabel("Z2", 1)).isEqualTo("Z2");
        assertThat(RouteColoring.zoneLabel("Endurance", 1)).isEqualTo("Z2 Endurance");
    }

    @Test
    void smoothingIgnoresGaps() {
        Double[] smoothed = RouteColoring.smooth(Arrays.asList(100, null, 200), 3);

        assertThat(smoothed).containsExactly(150.0, 150.0, 150.0);
    }
}
