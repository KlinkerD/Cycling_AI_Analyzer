package com.cycling.config;

import com.cycling.messaging.MqttMessaging;
import com.cycling.messaging.MqttSettings;
import com.cycling.messaging.Topics;
import com.cycling.messaging.contract.ActivityDetected;
import com.cycling.messaging.contract.AnalysisResult;
import com.cycling.messaging.contract.DeliveryServiceStatus;
import com.cycling.messaging.contract.RecommendationUpdated;
import com.cycling.repository.ActivityAnalysisRepository;
import com.cycling.repository.AnalysisJobRepository;
import com.cycling.service.ActivityDetectedHandler;
import com.cycling.service.AnalysisJobService;
import com.cycling.service.AnalysisService;
import com.cycling.service.RecommendationStore;
import com.cycling.service.ReportResendService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.time.Clock;
import java.time.Duration;

/** MQTT-Anbindung an intervals-service, ai-service, recommendation-service, report-service und delivery-service. */
@Configuration
public class MessagingConfig {

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
    public AnalysisJobService analysisJobService(AnalysisService analysisService,
                                                 AnalysisJobRepository jobRepository,
                                                 MqttMessaging messaging,
                                                 ObjectMapper objectMapper,
                                                 Clock clock,
                                                 @Value("${analysis.job-timeout:15m}") Duration timeout) {
        return new AnalysisJobService(analysisService, jobRepository, messaging, objectMapper, clock, timeout);
    }

    @Bean
    public RecommendationStore recommendationStore(MqttMessaging messaging, Clock clock,
                                                   @Value("${recommendation.refresh-timeout:10s}") Duration refreshTimeout) {
        return new RecommendationStore(messaging, clock, refreshTimeout);
    }

    @Bean
    public ActivityDetectedHandler activityDetectedHandler(AnalysisJobService jobService,
                                                           ActivityAnalysisRepository repository) {
        return new ActivityDetectedHandler(jobService, repository);
    }

    /** Handler registrieren und erst danach verbinden, damit keine nachgelieferte Nachricht verloren geht. */
    @EventListener(ApplicationReadyEvent.class)
    public void connectMqtt(ApplicationReadyEvent event) {
        var context = event.getApplicationContext();
        MqttMessaging messaging = context.getBean(MqttMessaging.class);
        AnalysisJobService jobs = context.getBean(AnalysisJobService.class);
        RecommendationStore recommendations = context.getBean(RecommendationStore.class);
        ReportResendService resend = context.getBean(ReportResendService.class);
        ActivityDetectedHandler detected = context.getBean(ActivityDetectedHandler.class);

        messaging.subscribe(Topics.INTERVALS_ACTIVITY_DETECTED, ActivityDetected.class, detected::onActivityDetected);
        messaging.subscribe(Topics.AI_ANALYSIS_RESULT, AnalysisResult.class, jobs::complete);
        messaging.subscribe(Topics.RECOMMENDATION_UPDATED, RecommendationUpdated.class, recommendations::onUpdate);
        messaging.subscribe(Topics.DELIVERY_STATUS, DeliveryServiceStatus.class, resend::updateStatus);
        messaging.start();
    }
}
