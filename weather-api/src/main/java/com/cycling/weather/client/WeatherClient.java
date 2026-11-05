package com.cycling.weather.client;

import com.cycling.weather.api.WeatherApi;
import com.cycling.weather.api.WeatherQuery;
import com.cycling.weather.api.WeatherResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

/**
 * REST-Client fuer den weather-service. Wiederholt einen fehlgeschlagenen Aufruf genau einmal; danach
 * {@link WeatherUnavailableException}. Das Wetter ist fuer den Aufrufer ein Extra, er macht ohne weiter.
 */
public class WeatherClient {

    private static final Logger log = LoggerFactory.getLogger(WeatherClient.class);

    private final RestClient restClient;

    /** @param restClient mit der Basis-URL des weather-service */
    public WeatherClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /** Client mit JDK-HttpClient und den angegebenen Timeouts. */
    public static WeatherClient create(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return new WeatherClient(RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build());
    }

    /**
     * Wetter zu den Punkten, gleiche Reihenfolge. Leere Eingabe = leere Antwort ohne Aufruf.
     *
     * @throws WeatherUnavailableException wenn auch die Wiederholung scheitert
     */
    public List<WeatherResult.Point> points(List<WeatherQuery.Point> points) {
        if (points == null || points.isEmpty()) return List.of();
        WeatherQuery query = new WeatherQuery(points);
        try {
            return call(query);
        } catch (RestClientException | WeatherUnavailableException first) {
            log.warn("weather-service: erster Versuch fehlgeschlagen ({}), wiederhole einmal", first.getMessage());
            try {
                return call(query);
            } catch (RestClientException e) {
                throw new WeatherUnavailableException("weather-service nicht verfuegbar: " + e.getMessage(), e);
            }
        }
    }

    private List<WeatherResult.Point> call(WeatherQuery query) {
        WeatherResult result = restClient.post()
                .uri(WeatherApi.POINTS)
                .contentType(MediaType.APPLICATION_JSON)
                .body(query)
                .retrieve()
                .body(WeatherResult.class);
        if (result == null || result.points() == null) {
            throw new WeatherUnavailableException("weather-service lieferte keine Daten", null);
        }
        return result.points();
    }
}
