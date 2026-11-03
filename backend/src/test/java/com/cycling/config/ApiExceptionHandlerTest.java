package com.cycling.config;

import com.cycling.controller.OverviewController;
import com.cycling.service.IntervalsClient;
import com.cycling.service.IntervalsUnavailableException;
import com.cycling.service.RecommendationStore;
import com.cycling.service.RecommendationUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fehler des intervals-service kommen als 502/503 mit verstaendlichem Text beim Frontend an, nicht als 500. */
class ApiExceptionHandlerTest {

    private final IntervalsClient intervals = mock(IntervalsClient.class);
    private final RecommendationStore recommendations = mock(RecommendationStore.class);
    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new OverviewController(intervals, recommendations))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    void authProblemIsBadGateway() throws Exception {
        when(intervals.getWellness(anyString(), anyString())).thenThrow(new IntervalsUnavailableException(
                HttpStatus.BAD_GATEWAY, "INTERVALS_AUTH", "API-Key pruefen", null, null));

        mvc.perform(get("/api/overview/wellness").param("from", "2026-11-01").param("to", "2026-11-03"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("API-Key pruefen"))
                .andExpect(jsonPath("$.code").value("INTERVALS_AUTH"));
    }

    @Test
    void rateLimitIsServiceUnavailableWithRetryAfter() throws Exception {
        when(intervals.getWellness(anyString(), anyString())).thenThrow(new IntervalsUnavailableException(
                HttpStatus.SERVICE_UNAVAILABLE, "INTERVALS_RATE_LIMIT", "Rate-Limit", 30L, null));

        mvc.perform(get("/api/overview/wellness").param("from", "2026-11-01").param("to", "2026-11-03"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "30"));
    }

    @Test
    void refreshEvictsCachesOfIntervalsService() throws Exception {
        mvc.perform(get("/api/overview/zones").param("refresh", "true")).andExpect(status().isOk());

        verify(intervals).evictCaches();
    }

    @Test
    void missingRecommendationIsServiceUnavailableWithMessage() throws Exception {
        when(recommendations.current()).thenThrow(new RecommendationUnavailableException("noch nicht da"));

        mvc.perform(get("/api/overview/recommendation"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("RECOMMENDATION_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("noch nicht da"));
    }
}
