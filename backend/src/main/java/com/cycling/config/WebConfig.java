package com.cycling.config;

import com.cycling.service.IntervalsClient;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:4200", "http://localhost")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }

    /**
     * REST-Client zum intervals-service. Lese-Timeout grosszuegig, weil der Service bei Rate-Limits von
     * intervals.icu selbst ein paar Sekunden wartet und wiederholt; hier wird nicht wiederholt.
     */
    @Bean
    public IntervalsClient intervalsClient(
            @Value("${intervals-service.base-url}") String baseUrl,
            @Value("${intervals-service.connect-timeout:5s}") Duration connectTimeout,
            @Value("${intervals-service.read-timeout:60s}") Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return new IntervalsClient(RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build());
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
}
