package com.cycling.weather;

import com.cycling.weather.api.WeatherApi;
import com.cycling.weather.api.WeatherQuery;
import com.cycling.weather.api.WeatherResult;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WeatherServiceTest {

    private static final long T0 = Instant.parse("2026-11-01T07:00:00Z").getEpochSecond();

    /** Merkt sich die Aufrufe; jede Reihe hat 07:00 = 6 Grad, 08:00 = 9 Grad. */
    private static final class FakeSource implements WeatherSource {
        final List<List<Location>> calls = new ArrayList<>();
        final List<LocalDate[]> ranges = new ArrayList<>();

        @Override
        public List<Series> fetch(List<Location> locations, LocalDate fromUtc, LocalDate toUtc) {
            calls.add(List.copyOf(locations));
            ranges.add(new LocalDate[]{fromUtc, toUtc});
            Series s = new Series(60, List.of(T0, T0 + 3600), List.of(6.0, 9.0), List.of(10.0, 10.0),
                    List.of(20.0, 20.0), List.of(270.0, 270.0), List.of(0.0, 1.0));
            return Collections.nCopies(locations.size(), s);
        }
    }

    private final FakeSource source = new FakeSource();
    private final WeatherService service = new WeatherService(source, Caffeine.newBuilder().build());

    private static WeatherQuery.Point point(double lat, double lng, String time) {
        return new WeatherQuery.Point(lat, lng, time == null ? null : Instant.parse(time));
    }

    @Test
    void interpolatesEachPointInRequestOrder() {
        WeatherResult result = service.points(new WeatherQuery(List.of(
                point(48.1, 11.5, "2026-11-01T07:00:00Z"),
                point(48.2, 11.6, "2026-11-01T07:40:00Z"))));

        assertThat(result.source()).isEqualTo("open-meteo");
        assertThat(result.points()).extracting(WeatherResult.Point::temperatureC).containsExactly(6.0, 8.0);
        assertThat(result.points()).extracting(WeatherResult.Point::time)
                .containsExactly(Instant.parse("2026-11-01T07:00:00Z"), Instant.parse("2026-11-01T07:40:00Z"));
    }

    @Test
    void pointsInSameGridCellShareOneLocation() {
        service.points(new WeatherQuery(List.of(
                point(48.1001, 11.5001, "2026-11-01T07:00:00Z"),
                point(48.1049, 11.4951, "2026-11-01T07:10:00Z"),
                point(48.3, 11.5, "2026-11-01T07:20:00Z"))));

        assertThat(source.calls).singleElement().satisfies(locs -> assertThat(locs).hasSize(2));
    }

    @Test
    void secondRequestComesFromCache() {
        WeatherQuery query = new WeatherQuery(List.of(point(48.1, 11.5, "2026-11-01T07:30:00Z")));

        WeatherResult first = service.points(query);
        WeatherResult second = service.points(query);

        assertThat(second).isEqualTo(first);
        assertThat(source.calls).hasSize(1);
    }

    @Test
    void rangeCoversAllUtcDays() {
        service.points(new WeatherQuery(List.of(
                point(48.1, 11.5, "2026-11-01T23:50:00Z"),
                point(48.1, 11.5, "2026-11-02T00:10:00Z"))));

        // ein Tag mehr, damit Punkte nach dem letzten Wert des Tages (23:00 bzw. 23:45) nicht leer bleiben
        assertThat(source.ranges.getFirst()).containsExactly(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3));
    }

    @Test
    void seriesWithGapsIsNotCached() {
        WeatherSource gappy = (locations, from, to) -> {
            source.calls.add(List.copyOf(locations));
            Series s = new Series(60, List.of(T0, T0 + 3600), java.util.Arrays.asList(6.0, null),
                    List.of(10.0, 10.0), List.of(20.0, 20.0), List.of(270.0, 270.0), List.of(0.0, 1.0));
            return Collections.nCopies(locations.size(), s);
        };
        WeatherService uncached = new WeatherService(gappy, Caffeine.newBuilder().build());
        WeatherQuery query = new WeatherQuery(List.of(point(48.1, 11.5, "2026-11-01T07:10:00Z")));

        uncached.points(query);
        uncached.points(query);

        assertThat(source.calls).hasSize(2);
    }

    @Test
    void rejectsInvalidQueries() {
        assertThatThrownBy(() -> service.points(new WeatherQuery(List.of())))
                .isInstanceOf(InvalidQueryException.class);
        assertThatThrownBy(() -> service.points(new WeatherQuery(List.of(point(91, 0, "2026-11-01T07:00:00Z")))))
                .isInstanceOf(InvalidQueryException.class);
        assertThatThrownBy(() -> service.points(new WeatherQuery(List.of(point(48, 11, null)))))
                .hasMessageContaining("Zeit fehlt");
        assertThatThrownBy(() -> service.points(new WeatherQuery(
                Collections.nCopies(WeatherApi.MAX_POINTS + 1, point(48, 11, "2026-11-01T07:00:00Z")))))
                .isInstanceOf(InvalidQueryException.class);
        assertThat(source.calls).isEmpty();
    }
}
