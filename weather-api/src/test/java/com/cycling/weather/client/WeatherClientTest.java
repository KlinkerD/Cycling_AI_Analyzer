package com.cycling.weather.client;

import com.cycling.weather.api.WeatherApi;
import com.cycling.weather.api.WeatherQuery;
import com.cycling.weather.api.WeatherResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.twice;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WeatherClientTest {

    private static final String BODY = """
            {"points":[{"lat":48.13,"lng":11.57,"time":"2026-11-01T07:30:00Z","temperatureC":7.4,
            "windSpeedKmh":14.0,"windGustKmh":28.5,"windDirectionDeg":225.0,"precipitationMmPerHour":0.0}],
            "source":"open-meteo"}""";
    private static final List<WeatherQuery.Point> POINTS =
            List.of(new WeatherQuery.Point(48.13, 11.57, "2026-11-01T07:30:00Z"));

    private MockRestServiceServer server;
    private WeatherClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://weather");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new WeatherClient(builder.build());
    }

    @Test
    void returnsPoints() {
        server.expect(once(), requestTo("http://weather" + WeatherApi.POINTS)).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(BODY, MediaType.APPLICATION_JSON));

        List<WeatherResult.Point> points = client.points(POINTS);

        assertThat(points).singleElement().satisfies(p -> {
            assertThat(p.temperatureC()).isEqualTo(7.4);
            assertThat(p.windDirectionDeg()).isEqualTo(225.0);
        });
        server.verify();
    }

    @Test
    void retriesOnceAfterError() {
        server.expect(once(), requestTo("http://weather" + WeatherApi.POINTS)).andRespond(withServerError());
        server.expect(once(), requestTo("http://weather" + WeatherApi.POINTS))
                .andRespond(withSuccess(BODY, MediaType.APPLICATION_JSON));

        assertThat(client.points(POINTS)).hasSize(1);
        server.verify();
    }

    @Test
    void givesUpAfterSecondError() {
        server.expect(twice(), requestTo("http://weather" + WeatherApi.POINTS)).andRespond(withServerError());

        assertThatThrownBy(() -> client.points(POINTS)).isInstanceOf(WeatherUnavailableException.class);
        server.verify();
    }

    @Test
    void emptyInputMakesNoCall() {
        assertThat(client.points(List.of())).isEmpty();
        server.verify();
    }
}
