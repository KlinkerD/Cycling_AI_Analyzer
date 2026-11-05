package com.cycling.weather.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Die Records muessen JSON-Roundtrips ueberstehen, weil weather-service und report-service sie austauschen. */
class WeatherApiJsonTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    private <T> T roundTrip(T value, Class<T> type) throws Exception {
        return mapper.readValue(mapper.writeValueAsString(value), type);
    }

    @Test
    void queryRoundTrip() throws Exception {
        WeatherQuery query = new WeatherQuery(List.of(new WeatherQuery.Point(48.13, 11.57, Instant.parse("2026-11-01T07:30:00Z"))));

        assertThat(roundTrip(query, WeatherQuery.class)).isEqualTo(query);
    }

    @Test
    void timeIsIsoStringInJson() throws Exception {
        WeatherQuery query = new WeatherQuery(List.of(new WeatherQuery.Point(48.13, 11.57, Instant.parse("2026-11-01T07:30:00Z"))));

        assertThat(mapper.writeValueAsString(query)).contains("\"time\":\"2026-11-01T07:30:00Z\"");
    }

    @Test
    void resultRoundTripKeepsNulls() throws Exception {
        WeatherResult result = new WeatherResult(List.of(
                new WeatherResult.Point(48.13, 11.57, Instant.parse("2026-11-01T07:30:00Z"), 7.4, 14.0, 28.5, 225.0, 0.0),
                new WeatherResult.Point(48.2, 11.6, Instant.parse("2026-11-01T07:40:00Z"), null, null, null, null, null)),
                "open-meteo");

        assertThat(roundTrip(result, WeatherResult.class)).isEqualTo(result);
    }
}
