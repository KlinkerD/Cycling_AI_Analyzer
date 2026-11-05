package com.cycling.weather;

import com.cycling.weather.api.WeatherApi;
import com.cycling.weather.api.WeatherResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** REST-Endpoint und Fehlerantworten (ProblemDetail) ohne Spring-Kontext. */
class WeatherControllerTest {

    private static final String QUERY = """
            {"points":[{"lat":48.1,"lng":11.5,"time":"2026-11-01T07:00:00Z"}]}""";

    private final WeatherService service = mock(WeatherService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new WeatherController(service)).build();

    @Test
    void returnsPoints() throws Exception {
        when(service.points(any())).thenReturn(new WeatherResult(List.of(
                new WeatherResult.Point(48.1, 11.5, Instant.parse("2026-11-01T07:00:00Z"), 6.0, 10.0, 20.0, 270.0, 0.0)), "open-meteo"));

        mvc.perform(post(WeatherApi.POINTS).contentType(MediaType.APPLICATION_JSON).content(QUERY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points[0].temperatureC").value(6.0))
                .andExpect(jsonPath("$.source").value("open-meteo"));
    }

    @Test
    void invalidQueryIs400() throws Exception {
        when(service.points(any())).thenThrow(new InvalidQueryException("Keine Punkte angegeben"));

        mvc.perform(post(WeatherApi.POINTS).contentType(MediaType.APPLICATION_JSON).content("{\"points\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Keine Punkte angegeben"));
    }

    @Test
    void unreadableBodyIs400() throws Exception {
        mvc.perform(post(WeatherApi.POINTS).contentType(MediaType.APPLICATION_JSON).content("nicht json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sourceFailureKeepsStatus() throws Exception {
        when(service.points(any())).thenThrow(
                new WeatherSourceException(HttpStatus.SERVICE_UNAVAILABLE, "Open-Meteo nicht verfuegbar", null));

        mvc.perform(post(WeatherApi.POINTS).contentType(MediaType.APPLICATION_JSON).content(QUERY))
                .andExpect(status().isServiceUnavailable());
    }
}
