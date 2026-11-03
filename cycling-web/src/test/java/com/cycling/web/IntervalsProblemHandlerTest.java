package com.cycling.web;

import com.cycling.intervals.client.IntervalsUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fehler des intervals-service kommen als 502/503 mit verstaendlichem Text beim Frontend an, nicht als 500. */
class IntervalsProblemHandlerTest {

    @RestController
    static class FailingController {
        @GetMapping("/fail")
        String fail(@RequestParam String kind) {
            throw switch (kind) {
                case "auth" -> new IntervalsUnavailableException(HttpStatus.BAD_GATEWAY, "INTERVALS_AUTH",
                        "API-Key pruefen", null, null);
                case "rate" -> new IntervalsUnavailableException(HttpStatus.SERVICE_UNAVAILABLE,
                        "INTERVALS_RATE_LIMIT", "Rate-Limit", 30L, null);
                default -> new IntervalsUnavailableException("weg", null);
            };
        }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new FailingController())
            .setControllerAdvice(new IntervalsProblemHandler())
            .build();

    @Test
    void authProblemIsBadGateway() throws Exception {
        mvc.perform(get("/fail").param("kind", "auth"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("API-Key pruefen"))
                .andExpect(jsonPath("$.code").value("INTERVALS_AUTH"));
    }

    @Test
    void rateLimitIsServiceUnavailableWithRetryAfter() throws Exception {
        mvc.perform(get("/fail").param("kind", "rate"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "30"));
    }

    @Test
    void unreachableServiceIsServiceUnavailable() throws Exception {
        mvc.perform(get("/fail").param("kind", "down"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(IntervalsUnavailableException.SERVICE_UNREACHABLE));
    }
}
