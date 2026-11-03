package com.cycling.training.api;

import com.cycling.intervals.api.IntervalsAthleteThresholds;
import com.cycling.intervals.api.IntervalsAthleteZones;
import com.cycling.intervals.api.IntervalsWellnessPoint;
import com.cycling.intervals.api.IntervalsZone;
import com.cycling.intervals.client.IntervalsClient;
import com.cycling.intervals.client.IntervalsUnavailableException;
import com.cycling.web.IntervalsProblemHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FitnessControllerTest {

    private final IntervalsClient intervals = mock(IntervalsClient.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new FitnessController(intervals))
            .setControllerAdvice(new IntervalsProblemHandler())
            .build();

    @Test
    void athleteCombinesThresholdsAndZones() throws Exception {
        when(intervals.athleteZonesOrEmpty()).thenReturn(new IntervalsAthleteZones(
                new IntervalsAthleteThresholds("Dan", 250, 235, 170, 190, 72.5),
                List.of(new IntervalsZone(2, "Endurance", 0.55, 0.75, 138, 188, "#42a5f5")),
                List.of(), "lthr"));

        mvc.perform(get("/api/fitness/athlete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.thresholds.indoorFtp").value(235))
                .andExpect(jsonPath("$.powerZones[0].upperValue").value(188))
                .andExpect(jsonPath("$.hrReference").value("lthr"));
        verify(intervals, never()).evictCachesQuietly();
    }

    @Test
    void refreshEvictsCachesOfIntervalsService() throws Exception {
        when(intervals.athleteZonesOrEmpty()).thenReturn(new IntervalsAthleteZones(
                IntervalsClient.emptyThresholds(), List.of(), List.of(), "lthr"));

        mvc.perform(get("/api/fitness/athlete").param("refresh", "true")).andExpect(status().isOk());

        verify(intervals).evictCachesQuietly();
    }

    @Test
    void wellnessDropsRampRate() throws Exception {
        when(intervals.wellness(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3)))
                .thenReturn(List.of(new IntervalsWellnessPoint("2026-11-03", 55.0, 60.0, -5.0, 3.2)));

        mvc.perform(get("/api/fitness/wellness").param("from", "2026-11-01").param("to", "2026-11-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].date").value("2026-11-03"))
                .andExpect(jsonPath("$[0].form").value(-5.0))
                .andExpect(jsonPath("$[0].rampRate").doesNotExist());
    }

    @Test
    void wellnessRateLimitIsServiceUnavailableWithRetryAfter() throws Exception {
        when(intervals.wellness(any(), any())).thenThrow(new IntervalsUnavailableException(
                HttpStatus.SERVICE_UNAVAILABLE, "INTERVALS_RATE_LIMIT", "Rate-Limit", 30L, null));

        mvc.perform(get("/api/fitness/wellness").param("from", "2026-11-01").param("to", "2026-11-03"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.code").value("INTERVALS_RATE_LIMIT"));
    }
}
