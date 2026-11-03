package com.cycling.intervals;

import com.cycling.messaging.MqttMessaging;
import com.cycling.messaging.MqttSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

@Configuration
public class IntervalsConfig {

    private static final Logger log = LoggerFactory.getLogger(IntervalsConfig.class);

    @Bean
    public IntervalsProperties intervalsProperties(
            @Value("${intervals.api.key:}") String apiKey,
            @Value("${intervals.athlete.id:}") String athleteId,
            @Value("${intervals.base-url:https://intervals.icu/api/v1}") String baseUrl,
            @Value("${intervals.max-retries:2}") int maxRetries,
            @Value("${intervals.max-retry-after:10s}") Duration maxRetryAfter,
            @Value("${intervals.max-concurrent:4}") int maxConcurrent) {
        IntervalsProperties properties =
                new IntervalsProperties(apiKey, athleteId, baseUrl, maxRetries, maxRetryAfter, maxConcurrent);
        if (!properties.isConfigured()) {
            log.warn("INTERVALS_API_KEY / INTERVALS_ATHLETE_ID fehlen - alle Abrufe antworten mit INTERVALS_NOT_CONFIGURED");
        }
        return properties;
    }

    @Bean
    public IntervalsHttpClient intervalsHttpClient(
            IntervalsProperties properties,
            @Value("${intervals.connect-timeout:5s}") Duration connectTimeout,
            @Value("${intervals.read-timeout:30s}") Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        RestClient restClient = RestClient.builder().requestFactory(requestFactory).build();
        return new IntervalsHttpClient(restClient, properties, duration -> Thread.sleep(duration));
    }

    @Bean
    public IntervalsGateway intervalsGateway(IntervalsHttpClient http) {
        return new IntervalsGateway(http);
    }

    @Bean
    public IntervalsService intervalsService(IntervalsGateway gateway) {
        return new IntervalsService(gateway);
    }

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

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
    public IntervalsPoller intervalsPoller(IntervalsGateway gateway, MqttMessaging messaging, Clock clock,
                                           @Value("${intervals.poll.window-days:2}") int windowDays) {
        return new IntervalsPoller(gateway, messaging, clock, windowDays);
    }

    /** Der Service veroeffentlicht nur; Verbindung erst aufbauen, wenn alles steht. */
    @EventListener(ApplicationReadyEvent.class)
    public void connectMqtt(ApplicationReadyEvent event) {
        event.getApplicationContext().getBean(MqttMessaging.class).start();
    }
}
