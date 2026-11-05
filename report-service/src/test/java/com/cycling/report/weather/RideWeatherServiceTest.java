package com.cycling.report.weather;

import com.cycling.report.api.StreamDataDto;
import com.cycling.report.api.WeatherPointDto;
import com.cycling.weather.api.WeatherQuery;
import com.cycling.weather.api.WeatherResult;
import com.cycling.weather.client.WeatherClient;
import com.cycling.weather.client.WeatherUnavailableException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RideWeatherServiceTest {

    private final WeatherClient client = mock(WeatherClient.class);
    private final WeatherPointRepository repository = mock(WeatherPointRepository.class);
    private final RideWeatherService service = new RideWeatherService(client, repository);

    /** 15 min, 1 Probe pro 10 s. */
    private static StreamDataDto ride() {
        List<Integer> time = new ArrayList<>();
        List<Double> distance = new ArrayList<>();
        List<Double> lat = new ArrayList<>();
        List<Double> lng = new ArrayList<>();
        for (int t = 0; t <= 900; t += 10) {
            time.add(t);
            distance.add(t * 5.0);
            lat.add(48.0 + t * 0.00005);
            lng.add(11.5);
        }
        return new StreamDataDto(distance, List.of(), List.of(), List.of(), List.of(), time, List.of(), lat, lng);
    }

    private static List<WeatherResult.Point> answer(List<WeatherQuery.Point> query) {
        return query.stream()
                .map(q -> new WeatherResult.Point(q.lat(), q.lng(), q.time(), 7.0, 12.0, 20.0, 180.0, 0.0))
                .toList();
    }

    @Test
    @SuppressWarnings("unchecked")
    void fetchesWeatherForEveryTenMinutesInUtc() {
        when(client.points(anyList())).thenAnswer(inv -> answer(inv.getArgument(0)));

        List<RideWeatherPoint> points = service.fetch("i1", false, "Ride", "2026-11-01T07:30:00Z", ride());

        ArgumentCaptor<List<WeatherQuery.Point>> query = ArgumentCaptor.forClass(List.class);
        verify(client).points(query.capture());
        assertThat(query.getValue()).extracting(WeatherQuery.Point::time)
                .containsExactly("2026-11-01T07:30:00Z", "2026-11-01T07:40:00Z", "2026-11-01T07:45:00Z");
        assertThat(points).hasSize(3).allSatisfy(p -> assertThat(p.temperatureC()).isEqualTo(7.0));
        assertThat(points.getFirst().windRelation()).isEqualTo(WindRelation.TAILWIND);
    }

    @Test
    void startWithoutOffsetIsReadAsUtc() {
        assertThat(RideWeatherService.parse("2026-11-01T07:30:00")).isEqualTo(Instant.parse("2026-11-01T07:30:00Z"));
        assertThat(RideWeatherService.parse("kaputt")).isNull();
    }

    @Test
    void indoorUnknownOrNoStartMakesNoCall() {
        assertThat(service.fetch("i1", true, "Ride", "2026-11-01T07:30:00Z", ride())).isEmpty();
        assertThat(service.fetch("i1", null, "Ride", "2026-11-01T07:30:00Z", ride())).isEmpty();
        assertThat(service.fetch("i1", false, "Ride", null, ride())).isEmpty();
        verify(client, never()).points(any());
    }

    @Test
    void unavailableServiceGivesNoWeather() {
        when(client.points(anyList())).thenThrow(new WeatherUnavailableException("weg", null));

        assertThat(service.fetch("i1", false, "Ride", "2026-11-01T07:30:00Z", ride())).isEmpty();
    }

    @Test
    void unexpectedErrorGivesNoWeather() {
        when(client.points(anyList())).thenThrow(new IllegalStateException("kaputt"));

        assertThat(service.fetch("i1", false, "Ride", "2026-11-01T07:30:00Z", ride())).isEmpty();
    }

    @Test
    void wrongAnswerSizeGivesNoWeather() {
        when(client.points(anyList())).thenReturn(List.of());

        assertThat(service.fetch("i1", false, "Ride", "2026-11-01T07:30:00Z", ride())).isEmpty();
    }

    @Test
    void disabledWithoutClient() {
        RideWeatherService disabled = new RideWeatherService(null, repository);

        assertThat(disabled.fetch("i1", false, "Ride", "2026-11-01T07:30:00Z", ride())).isEmpty();
    }

    @Test
    void replaceDeletesThenSaves() {
        RideWeatherPoint p = new RideWeatherPoint(
                new WeatherSample(0, 0.0, 48.0, 11.5, Instant.parse("2026-11-01T07:30:00Z"), 0.0),
                7.0, 12.0, 20.0, 180.0, 0.0);

        service.replace("i1", List.of(p));

        verify(repository).deleteByActivityId("i1");
        verify(repository).saveAll(anyList());
    }

    @Test
    void replaceWithNothingOnlyDeletes() {
        service.replace("i1", List.of());

        verify(repository).deleteByActivityId("i1");
        verify(repository, never()).saveAll(anyList());
    }

    @Test
    void summaryFromStoredPoints() {
        when(repository.findByActivityIdOrderByMinuteOfRideAsc("i1")).thenReturn(List.of(
                WeatherPoint.of("i1", new WeatherPointDto(0, 0.0, 48, 11, "t0", 6.0, 10.0, 20.0, 180.0, 0.0)),
                WeatherPoint.of("i1", new WeatherPointDto(10, 3.0, 48, 11, "t1", 8.0, 14.0, 25.0, 180.0, 3.0))));

        WeatherSummary s = service.summary("i1").orElseThrow();

        assertThat(s.minTemperatureC()).isEqualTo(6.0);
        assertThat(s.maxTemperatureC()).isEqualTo(8.0);
        assertThat(s.avgTemperatureC()).isEqualTo(7.0);
        assertThat(s.avgWindSpeedKmh()).isEqualTo(12.0);
        assertThat(s.maxWindGustKmh()).isEqualTo(25.0);
        assertThat(s.precipitationMm()).isEqualTo(0.5);
    }

    @Test
    void noSummaryWithoutTemperature() {
        when(repository.findByActivityIdOrderByMinuteOfRideAsc("i1")).thenReturn(List.of());

        assertThat(service.summary("i1")).isEmpty();
    }
}
