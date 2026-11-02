package com.cycling.config;

import com.cycling.messaging.MqttMessaging;
import com.cycling.messaging.MqttSettings;
import com.cycling.messaging.Topics;
import com.cycling.messaging.contract.AnalysisResult;
import com.cycling.messaging.contract.RecommendationTextResult;
import com.cycling.repository.AnalysisJobRepository;
import com.cycling.service.AnalysisJobService;
import com.cycling.service.AnalysisService;
import com.cycling.service.RecommendationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.time.Clock;
import java.time.Duration;

/** MQTT-Anbindung an den ai-service. */
@Configuration
public class MessagingConfig {

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
    public AnalysisJobService analysisJobService(AnalysisService analysisService,
                                                 AnalysisJobRepository jobRepository,
                                                 MqttMessaging messaging,
                                                 ApplicationEventPublisher events,
                                                 ObjectMapper objectMapper,
                                                 Clock clock,
                                                 @Value("${analysis.job-timeout:15m}") Duration timeout) {
        return new AnalysisJobService(analysisService, jobRepository, messaging, events, objectMapper, clock, timeout);
    }

    /** Handler registrieren und erst danach verbinden, damit keine nachgelieferte Nachricht verloren geht. */
    @EventListener(ApplicationReadyEvent.class)
    public void connectMqtt(ApplicationReadyEvent event) {
        var context = event.getApplicationContext();
        MqttMessaging messaging = context.getBean(MqttMessaging.class);
        AnalysisJobService jobs = context.getBean(AnalysisJobService.class);
        RecommendationService recommendations = context.getBean(RecommendationService.class);

        messaging.subscribe(Topics.AI_ANALYSIS_RESULT, AnalysisResult.class, jobs::complete);
        messaging.subscribe(Topics.AI_RECOMMENDATION_RESULT, RecommendationTextResult.class, recommendations::applyText);
        messaging.start();
    }
}
