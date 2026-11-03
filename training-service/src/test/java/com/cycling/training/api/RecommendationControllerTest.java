package com.cycling.training.api;

import com.cycling.intervals.client.IntervalsUnavailableException;
import com.cycling.training.recommendation.Recommendation;
import com.cycling.training.recommendation.RecommendationEngine;
import com.cycling.web.IntervalsProblemHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecommendationControllerTest {

    private final RecommendationEngine engine = mock(RecommendationEngine.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new RecommendationController(engine))
            .setControllerAdvice(new IntervalsProblemHandler())
            .build();

    private static Recommendation recommendation(String requestId, String text, boolean textPending) {
        return new Recommendation(requestId, "TEMPO", "Tempo / Sweet Spot", "#ff9800", text, 8.0, 4.2, false,
                LocalDate.of(2026, 11, 3), LocalDateTime.of(2026, 11, 3, 9, 0), textPending, textPending);
    }

    @Test
    void getReturnsLatestWithoutRecomputing() throws Exception {
        when(engine.latest()).thenReturn(Optional.of(recommendation("r1", "Heute Sweet Spot.", false)));

        mvc.perform(get("/api/recommendation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("TEMPO"))
                .andExpect(jsonPath("$.text").value("Heute Sweet Spot."))
                .andExpect(jsonPath("$.textPending").value(false))
                .andExpect(jsonPath("$.refDate").value("2026-11-03"));
        verify(engine, never()).compute(any());
    }

    @Test
    void getComputesOnceAfterStart() throws Exception {
        when(engine.latest()).thenReturn(Optional.empty());
        when(engine.compute(any())).thenReturn(recommendation("r1", "Fallback", true));

        mvc.perform(get("/api/recommendation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.textPending").value(true));
    }

    @Test
    void getWithoutAnyStateAndIntervalsDownIsServiceUnavailable() throws Exception {
        when(engine.latest()).thenReturn(Optional.empty());
        when(engine.compute(any())).thenThrow(new IntervalsUnavailableException("weg", null));

        mvc.perform(get("/api/recommendation"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(IntervalsUnavailableException.SERVICE_UNREACHABLE));
    }

    @Test
    void refreshReturnsFirstStateOfNewComputation() throws Exception {
        when(engine.compute(any())).thenReturn(recommendation("r2", "Fallback", true));

        mvc.perform(post("/api/recommendation/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Fallback"))
                .andExpect(jsonPath("$.textPending").value(true));
    }

    @Test
    void failedRefreshKeepsLastState() throws Exception {
        when(engine.compute(any())).thenThrow(new IntervalsUnavailableException("weg", null));
        when(engine.latest()).thenReturn(Optional.of(recommendation("r1", "Alt", false)));

        mvc.perform(post("/api/recommendation/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Alt"));
    }
}
