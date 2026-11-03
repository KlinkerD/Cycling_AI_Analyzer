package com.cycling.ai;

import com.cycling.messaging.MqttMessaging;
import com.cycling.messaging.MqttSettings;
import com.cycling.messaging.Topics;
import com.cycling.messaging.contract.AiRequest;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;

@Configuration
public class AiConfig {

    @Bean(destroyMethod = "close")
    public MqttMessaging mqttMessaging(
            @Value("${mqtt.broker-url}") String brokerUrl,
            @Value("${mqtt.client-id}") String clientId,
            @Value("${mqtt.username:}") String username,
            @Value("${mqtt.password:}") String password,
            @Value("${mqtt.session-expiry:1d}") Duration sessionExpiry) {
        return new MqttMessaging(new MqttSettings(brokerUrl, clientId, username, password, sessionExpiry));
    }

    @Bean
    public GeminiClient geminiClient(@Value("${gemini.api.key}") String apiKey,
                                     @Value("${gemini.model}") String model) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("gemini.api.key fehlt (Umgebungsvariable GEMINI_API_KEY)");
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(120));
        RestClient restClient = RestClient.builder().requestFactory(requestFactory).build();
        ObjectMapper objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return new GeminiClient(restClient, objectMapper, apiKey, model);
    }

    @Bean
    public AiRequestHandler aiRequestHandler(GeminiClient gemini, MqttMessaging messaging,
                                             @Value("${gemini.min-interval:5s}") Duration minInterval) {
        AiRequestHandler handler = new AiRequestHandler(gemini, messaging, minInterval, Clock.systemUTC(),
                Thread::sleep);
        messaging.subscribe(Topics.AI_REQUEST_ALL, AiRequest.class, handler::handle);
        return handler;
    }

    /** Erst verbinden, wenn alle Handler registriert sind. */
    @EventListener(ApplicationReadyEvent.class)
    public void connectMqtt(ApplicationReadyEvent event) {
        event.getApplicationContext().getBean(MqttMessaging.class).start();
    }
}
