package com.cycling.intervals;

import com.cycling.intervals.api.IntervalsErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class IntervalsHttpClientTest {

    private static final String URL = "https://intervals.test/api/v1/athlete/i1";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final List<Duration> sleeps = new ArrayList<>();

    private IntervalsHttpClient client(String apiKey) {
        IntervalsProperties properties = new IntervalsProperties(apiKey, "i1", "https://intervals.test/api/v1",
                2, Duration.ofSeconds(10), 4);
        return new IntervalsHttpClient(builder.build(), properties, sleeps::add);
    }

    @Test
    void sendsBasicAuthAndReturnsBody() {
        server.expect(requestTo(URL))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Basic QVBJX0tFWTpzZWNyZXQ=")) // API_KEY:secret
                .andRespond(withSuccess("{\"name\":\"Dan\"}", MediaType.APPLICATION_JSON));

        assertThat(client("secret").get("/athlete/i1")).isEqualTo("{\"name\":\"Dan\"}");
        assertThat(sleeps).isEmpty();
        server.verify();
    }

    @Test
    void retriesRateLimitHonouringRetryAfter() {
        HttpHeaders retryAfter = new HttpHeaders();
        retryAfter.add(HttpHeaders.RETRY_AFTER, "2");
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(retryAfter));
        server.expect(requestTo(URL)).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client("secret").get("/athlete/i1")).isEqualTo("[]");
        assertThat(sleeps).containsExactly(Duration.ofSeconds(2));
        server.verify();
    }

    @Test
    void capsLongRetryAfter() {
        HttpHeaders retryAfter = new HttpHeaders();
        retryAfter.add(HttpHeaders.RETRY_AFTER, "120");
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(retryAfter));
        server.expect(requestTo(URL)).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        client("secret").get("/athlete/i1");

        assertThat(sleeps).containsExactly(Duration.ofSeconds(10));
    }

    @Test
    void persistentRateLimitBecomesRateLimitError() {
        HttpHeaders retryAfter = new HttpHeaders();
        retryAfter.add(HttpHeaders.RETRY_AFTER, "30");
        server.expect(times(3), requestTo(URL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(retryAfter));

        assertThatThrownBy(() -> client("secret").get("/athlete/i1"))
                .isInstanceOfSatisfying(IntervalsApiException.class, e -> {
                    assertThat(e.code()).isEqualTo(IntervalsErrorCode.INTERVALS_RATE_LIMIT);
                    assertThat(e.retryAfter()).isEqualTo(Duration.ofSeconds(30));
                });
        assertThat(sleeps).hasSize(2);
        server.verify();
    }

    @Test
    void retriesServerErrorsWithBackoff() {
        server.expect(times(2), requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(client("secret").get("/athlete/i1")).isEqualTo("{}");
        assertThat(sleeps).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(3));
        server.verify();
    }

    @Test
    void persistentNetworkErrorBecomesUnavailable() {
        server.expect(times(3), requestTo(URL)).andRespond(withException(new IOException("timeout")));

        assertThatThrownBy(() -> client("secret").get("/athlete/i1"))
                .isInstanceOfSatisfying(IntervalsApiException.class,
                        e -> assertThat(e.code()).isEqualTo(IntervalsErrorCode.INTERVALS_UNAVAILABLE));
        server.verify();
    }

    @Test
    void authErrorsAreNotRetried() {
        server.expect(times(1), requestTo(URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> client("secret").get("/athlete/i1"))
                .isInstanceOfSatisfying(IntervalsApiException.class,
                        e -> assertThat(e.code()).isEqualTo(IntervalsErrorCode.INTERVALS_AUTH));
        assertThat(sleeps).isEmpty();
        server.verify();
    }

    @Test
    void notFoundIsNotRetried() {
        server.expect(times(1), requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client("secret").get("/athlete/i1"))
                .isInstanceOfSatisfying(IntervalsApiException.class,
                        e -> assertThat(e.code()).isEqualTo(IntervalsErrorCode.NOT_FOUND));
        server.verify();
    }

    @Test
    void missingConfigurationFailsWithoutRequest() {
        assertThatThrownBy(() -> client("").get("/athlete/i1"))
                .isInstanceOfSatisfying(IntervalsApiException.class,
                        e -> assertThat(e.code()).isEqualTo(IntervalsErrorCode.INTERVALS_NOT_CONFIGURED));
        server.verify();
    }
}
