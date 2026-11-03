package com.cycling.recommendation;

import com.cycling.messaging.MqttMessaging;
import com.cycling.messaging.MqttSettings;
import com.cycling.messaging.Topics;
import com.cycling.messaging.contract.RecommendationRefreshRequest;
import com.cycling.messaging.contract.RecommendationTextResult;
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
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Verdrahtet den recommendation-service: REST zum intervals-service, MQTT rein (Refresh-Auftraege,
 * KI-Texte), MQTT raus (RecommendationUpdated, KI-Auftraege, DeliveryMessage).
 */
@Configuration
public class RecommendationConfig {

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
    public Clock recommendationClock(@Value("${recommendation.zone:Europe/Berlin}") String zone) {
        return Clock.system(parseZone(zone));
    }

    @Bean
    public IntervalsData intervalsData(@Value("${intervals-service.base-url}") String baseUrl,
                                       @Value("${intervals-service.connect-timeout:5s}") Duration connectTimeout,
                                       @Value("${intervals-service.read-timeout:30s}") Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return new IntervalsRestClient(RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build());
    }

    @Bean
    public RecommendationEngine recommendationEngine(IntervalsData intervals, MqttMessaging messaging, Clock clock,
                                                     @Value("${recommendation.text-timeout:90s}") Duration textTimeout) {
        return new RecommendationEngine(intervals, messaging, clock, textTimeout);
    }

    @Bean
    public RecommendationMessageBuilder recommendationMessageBuilder(@Value("${recommendation.targets:}") String targets) {
        return new RecommendationMessageBuilder(RecommendationTargets.parse(targets));
    }

    @Bean
    public DailyRecommendationJob dailyRecommendationJob(
            RecommendationEngine engine, RecommendationMessageBuilder messageBuilder, MqttMessaging messaging,
            Clock clock,
            @Value("${recommendation.daily.time:09:00}") String time,
            @Value("${recommendation.daily.catch-up-until:12:00}") String catchUpUntil,
            @Value("${recommendation.daily.text-wait:2m}") Duration textWait) {
        DailyRecommendationJob job = new DailyRecommendationJob(engine, messageBuilder, messaging, clock,
                parseTime("recommendation.daily.time", time),
                parseTime("recommendation.daily.catch-up-until", catchUpUntil), textWait);
        engine.addListener(job);
        return job;
    }

    @Bean
    public RecommendationSchedules recommendationSchedules(RecommendationEngine engine) {
        return new RecommendationSchedules(engine);
    }

    /** Handler registrieren und erst danach verbinden, damit keine nachgelieferte Nachricht verloren geht. */
    @EventListener(ApplicationReadyEvent.class)
    public void connectMqtt(ApplicationReadyEvent event) {
        var context = event.getApplicationContext();
        MqttMessaging messaging = context.getBean(MqttMessaging.class);
        RecommendationEngine engine = context.getBean(RecommendationEngine.class);

        messaging.subscribe(Topics.AI_RECOMMENDATION_RESULT, RecommendationTextResult.class, engine::applyText);
        messaging.subscribe(Topics.RECOMMENDATION_REFRESH, RecommendationRefreshRequest.class, engine::onRefresh);
        messaging.start();
    }

    static LocalTime parseTime(String key, String time) {
        try {
            return LocalTime.parse(time.trim());
        } catch (RuntimeException e) {
            throw new IllegalStateException(key + ": '" + time + "' ist keine Uhrzeit, erwartet HH:mm (z. B. 09:00)", e);
        }
    }

    static ZoneId parseZone(String zone) {
        try {
            return ZoneId.of(zone.trim());
        } catch (RuntimeException e) {
            throw new IllegalStateException("recommendation.zone: '" + zone + "' ist keine Zeitzone (z. B. Europe/Berlin)", e);
        }
    }
}
