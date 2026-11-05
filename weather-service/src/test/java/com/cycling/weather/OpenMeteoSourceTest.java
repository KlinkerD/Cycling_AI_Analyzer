package com.cycling.weather;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.twice;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.startsWith;

/** Aufruf und Antwort von Open-Meteo, mit aufgezeichneter Antwort statt echtem HTTP. */
class OpenMeteoSourceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneOffset.UTC);
    private static final String ONE_LOCATION = """
            {"latitude":48.12,"longitude":11.58,"utc_offset_seconds":0,
             "minutely_15":{"time":[1761980400,1761981300],
               "temperature_2m":[6.1,6.4],"wind_speed_10m":[12.2,13.0],"wind_gusts_10m":[22.0,24.1],
               "wind_direction_10m":[230,235],"precipitation":[0.0,null]}}""";
    private static final String TWO_LOCATIONS = "[" + ONE_LOCATION + "," + ONE_LOCATION + "]";

    private MockRestServiceServer forecastServer;
    private MockRestServiceServer archiveServer;
    private OpenMeteoSource source;

    @BeforeEach
    void setUp() {
        RestClient.Builder forecast = RestClient.builder().baseUrl("http://forecast");
        RestClient.Builder archive = RestClient.builder().baseUrl("http://archive");
        forecastServer = MockRestServiceServer.bindTo(forecast).build();
        archiveServer = MockRestServiceServer.bindTo(archive).build();
        source = new OpenMeteoSource(forecast.build(), archive.build(), CLOCK, 90, Duration.ZERO);
    }

    @Test
    void recentRideUsesForecastWithQuarterHours() {
        forecastServer.expect(once(), requestTo(startsWith("http://forecast/v1/forecast")))
                .andExpect(queryParam("latitude", "48.120"))
                .andExpect(queryParam("longitude", "11.580"))
                .andExpect(queryParam("minutely_15",
                        "temperature_2m,wind_speed_10m,wind_gusts_10m,wind_direction_10m,precipitation"))
                .andExpect(queryParam("start_date", "2026-11-01"))
                .andExpect(queryParam("end_date", "2026-11-01"))
                .andExpect(queryParam("timezone", "GMT"))
                .andExpect(queryParam("timeformat", "unixtime"))
                .andRespond(withSuccess(ONE_LOCATION, MediaType.APPLICATION_JSON));

        List<Series> series = source.fetch(List.of(new Location(48.12, 11.58)),
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1));

        assertThat(series).singleElement().satisfies(s -> {
            assertThat(s.stepMinutes()).isEqualTo(15);
            assertThat(s.epochSeconds()).containsExactly(1761980400L, 1761981300L);
            assertThat(s.temperatureC()).containsExactly(6.1, 6.4);
            assertThat(s.windDirectionDeg()).containsExactly(230.0, 235.0);
            assertThat(s.precipitation()).containsExactly(0.0, null);
        });
        forecastServer.verify();
    }

    @Test
    void emptyForecastFallsBackToArchive() {
        String empty = """
                {"latitude":48.12,"longitude":11.58,"utc_offset_seconds":0,
                 "minutely_15":{"time":[1761980400,1761981300],
                   "temperature_2m":[null,null],"wind_speed_10m":[null,null],"wind_gusts_10m":[null,null],
                   "wind_direction_10m":[null,null],"precipitation":[null,null]}}""";
        String archived = """
                {"latitude":48.12,"longitude":11.58,"utc_offset_seconds":0,
                 "hourly":{"time":[1761980400,1761984000],
                   "temperature_2m":[6.0,7.0],"wind_speed_10m":[10,11],"wind_gusts_10m":[20,21],
                   "wind_direction_10m":[200,210],"precipitation":[0.0,0.2]}}""";
        forecastServer.expect(once(), requestTo(startsWith("http://forecast/v1/forecast")))
                .andRespond(withSuccess(empty, MediaType.APPLICATION_JSON));
        archiveServer.expect(once(), requestTo(startsWith("http://archive/v1/archive")))
                .andExpect(queryParam("hourly",
                        "temperature_2m,wind_speed_10m,wind_gusts_10m,wind_direction_10m,precipitation"))
                .andRespond(withSuccess(archived, MediaType.APPLICATION_JSON));

        List<Series> series = source.fetch(List.of(new Location(48.12, 11.58)),
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1));

        assertThat(series).singleElement().satisfies(s -> {
            assertThat(s.stepMinutes()).isEqualTo(60);
            assertThat(s.temperatureC()).containsExactly(6.0, 7.0);
        });
        forecastServer.verify();
        archiveServer.verify();
    }

    @Test
    void oldRideUsesHourlyArchive() {
        archiveServer.expect(once(), requestTo(startsWith("http://archive/v1/archive")))
                .andExpect(queryParam("hourly",
                        "temperature_2m,wind_speed_10m,wind_gusts_10m,wind_direction_10m,precipitation"))
                .andRespond(withSuccess(ONE_LOCATION.replace("minutely_15", "hourly"), MediaType.APPLICATION_JSON));

        List<Series> series = source.fetch(List.of(new Location(48.12, 11.58)),
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1));

        assertThat(series.getFirst().stepMinutes()).isEqualTo(60);
        archiveServer.verify();
    }

    @Test
    void severalLocationsInOneCall() {
        forecastServer.expect(once(), requestTo(startsWith("http://forecast/v1/forecast")))
                .andExpect(queryParam("latitude", "48.120,48.140"))
                .andExpect(queryParam("longitude", "11.580,11.600"))
                .andRespond(withSuccess(TWO_LOCATIONS, MediaType.APPLICATION_JSON));

        List<Series> series = source.fetch(List.of(new Location(48.12, 11.58), new Location(48.14, 11.6)),
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1));

        assertThat(series).hasSize(2);
        forecastServer.verify();
    }

    @Test
    void retriesOnceAfter429() {
        forecastServer.expect(once(), requestTo(startsWith("http://forecast"))).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        forecastServer.expect(once(), requestTo(startsWith("http://forecast")))
                .andRespond(withSuccess(ONE_LOCATION, MediaType.APPLICATION_JSON));

        assertThat(source.fetch(List.of(new Location(48.12, 11.58)), LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 1))).hasSize(1);
        forecastServer.verify();
    }

    @Test
    void secondTooManyRequestsIsUnavailable() {
        forecastServer.expect(twice(), requestTo(startsWith("http://forecast"))).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> source.fetch(List.of(new Location(48.12, 11.58)), LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 1)))
                .isInstanceOfSatisfying(WeatherSourceException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void badRequestIsBadGateway() {
        forecastServer.expect(once(), requestTo(startsWith("http://forecast")))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("{\"error\":true,\"reason\":\"out of range\"}"));

        assertThatThrownBy(() -> source.fetch(List.of(new Location(48.12, 11.58)), LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 1)))
                .isInstanceOfSatisfying(WeatherSourceException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void wrongLocationCountIsBadGateway() {
        assertThatThrownBy(() -> OpenMeteoSource.parse(ONE_LOCATION, "minutely_15", 15, 2))
                .isInstanceOf(WeatherSourceException.class);
        assertThatThrownBy(() -> OpenMeteoSource.parse("kein json", "minutely_15", 15, 1))
                .isInstanceOf(WeatherSourceException.class);
    }
}
