package com.cycling.weather;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

/** Verdrahtet Open-Meteo, Cache und Service. */
@Configuration
class WeatherConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    WeatherSource weatherSource(@Value("${open-meteo.forecast-url}") String forecastUrl,
                                @Value("${open-meteo.archive-url}") String archiveUrl,
                                @Value("${open-meteo.user-agent}") String userAgent,
                                @Value("${open-meteo.connect-timeout:5s}") Duration connectTimeout,
                                @Value("${open-meteo.read-timeout:8s}") Duration readTimeout,
                                @Value("${open-meteo.forecast-past-days:90}") int forecastPastDays,
                                @Value("${open-meteo.retry-delay:2s}") Duration retryDelay,
                                Clock clock) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        RestClient.Builder base = RestClient.builder().requestFactory(requestFactory)
                .defaultHeader("User-Agent", userAgent);
        return new OpenMeteoSource(base.clone().baseUrl(forecastUrl).build(), base.clone().baseUrl(archiveUrl).build(),
                clock, forecastPastDays, retryDelay);
    }

    @Bean
    WeatherService weatherService(WeatherSource source,
                                  @Value("${weather.cache-ttl:1h}") Duration cacheTtl) {
        return new WeatherService(source, Caffeine.newBuilder()
                .expireAfterWrite(cacheTtl)
                .maximumSize(5_000)
                .build());
    }
}
