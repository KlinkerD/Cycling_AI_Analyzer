package com.cycling.intervals;

import com.cycling.intervals.api.IntervalsActivity;
import com.cycling.intervals.api.IntervalsErrorCode;
import com.cycling.intervals.api.IntervalsWellnessPoint;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** REST-Endpoints und Fehlerantworten (ProblemDetail mit code) ohne Spring-Kontext. */
class IntervalsControllerTest {

    private final IntervalsService intervals = mock(IntervalsService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new IntervalsController(intervals))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    void listsActivities() throws Exception {
        when(intervals.activities(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3))).thenReturn(List.of(
                new IntervalsActivity("i1", "Runde", "2026-11-01T08:30:00", "Ride", 42000.0, 5400, 5600,
                        180.0, 195.0, 600.0, 140.0, 175.0, 7.8, 350.0)));

        mvc.perform(get("/api/intervals/activities").param("from", "2026-11-01").param("to", "2026-11-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("i1"))
                .andExpect(jsonPath("$[0].normalizedPower").value(195.0));
    }

    @Test
    void returnsWellnessWithRamp() throws Exception {
        when(intervals.wellness(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 3)))
                .thenReturn(List.of(new IntervalsWellnessPoint("2026-11-03", 55.0, 60.0, -5.0, 3.2)));

        mvc.perform(get("/api/intervals/wellness").param("from", "2026-11-02").param("to", "2026-11-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].form").value(-5.0))
                .andExpect(jsonPath("$[0].rampRate").value(3.2));
    }

    @Test
    void authProblemIsBadGateway() throws Exception {
        when(intervals.streams(anyString()))
                .thenThrow(new IntervalsApiException(IntervalsErrorCode.INTERVALS_AUTH, "API-Key pruefen"));

        mvc.perform(get("/api/intervals/activities/i1/streams"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("INTERVALS_AUTH"))
                .andExpect(jsonPath("$.detail").value("API-Key pruefen"));
    }

    @Test
    void rateLimitIsServiceUnavailableWithRetryAfter() throws Exception {
        when(intervals.athleteZones()).thenThrow(new IntervalsApiException(IntervalsErrorCode.INTERVALS_RATE_LIMIT,
                "Rate-Limit", Duration.ofSeconds(30), null));

        mvc.perform(get("/api/intervals/athlete/zones"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.code").value("INTERVALS_RATE_LIMIT"));
    }

    @Test
    void unknownActivityIsNotFound() throws Exception {
        when(intervals.activityZones("nope"))
                .thenThrow(new IntervalsApiException(IntervalsErrorCode.NOT_FOUND, "unbekannt"));

        mvc.perform(get("/api/intervals/activities/nope/zones"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void missingConfigurationIsServiceUnavailable() throws Exception {
        when(intervals.thresholds())
                .thenThrow(new IntervalsApiException(IntervalsErrorCode.INTERVALS_NOT_CONFIGURED, "fehlt"));

        mvc.perform(get("/api/intervals/athlete/thresholds"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("INTERVALS_NOT_CONFIGURED"));
    }

    @Test
    void invalidDatesAreBadRequest() throws Exception {
        mvc.perform(get("/api/intervals/wellness").param("from", "gestern").param("to", "2026-11-03"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(get("/api/intervals/wellness").param("from", "2026-11-04").param("to", "2026-11-03"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void evictClearsCaches() throws Exception {
        mvc.perform(post("/api/intervals/cache/evict")).andExpect(status().isNoContent());

        verify(intervals).evictAll();
    }
}
