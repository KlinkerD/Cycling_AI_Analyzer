package com.cycling.intervals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** Startet den ganzen Service ohne intervals.icu-Zugangsdaten und ohne Broker. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "intervals.api.key=",
        "intervals.athlete.id=",
        "intervals.poll.initial-delay=1h",
        "mqtt.broker-url=tcp://localhost:1",
        "mqtt.client-id=intervals-service-test"
})
class IntervalsServiceApplicationTest {

    @Autowired
    private Environment environment;

    private HttpResponse<String> get(String path) throws Exception {
        URI uri = URI.create("http://localhost:" + environment.getProperty("local.server.port") + path);
        try (HttpClient http = HttpClient.newHttpClient()) {
            return http.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test
    void healthIsUp() throws Exception {
        HttpResponse<String> response = get("/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"UP\"");
    }

    @Test
    void withoutCredentialsEveryCallSaysNotConfigured() throws Exception {
        HttpResponse<String> response = get("/api/intervals/athlete/thresholds");

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).contains("INTERVALS_NOT_CONFIGURED");
    }
}
