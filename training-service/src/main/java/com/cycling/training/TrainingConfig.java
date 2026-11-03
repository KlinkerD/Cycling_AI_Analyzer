package com.cycling.training;

import com.cycling.intervals.client.IntervalsClient;
import com.cycling.messaging.MqttMessaging;
import com.cycling.messaging.MqttSettings;
import com.cycling.messaging.Topics;
import com.cycling.messaging.contract.AiResult;
import com.cycling.messaging.contract.WellnessUpdated;
import com.cycling.training.fitness.FitnessMessageBuilder;
import com.cycling.training.fitness.FitnessSummaryJob;
import com.cycling.training.recommendation.DailyRecommendationJob;
import com.cycling.training.recommendation.IntervalsData;
import com.cycling.training.recommendation.IntervalsRestClient;
import com.cycling.training.recommendation.RecommendationEngine;
import com.cycling.training.recommendation.RecommendationMessageBuilder;
import com.cycling.training.recommendation.RecommendationSchedules;
import com.cycling.web.CyclingWebConfig;
import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Verdrahtet den training-service: Zustand des Athleten (Fitness, Form, Zonen) und die Tagesempfehlung.
 * <ul>
 *   <li>REST zum intervals-service, REST-API fuer das Frontend (/api/fitness, /api/recommendation)</li>
 *   <li>MQTT rein: WellnessUpdated (Abend-Zusammenfassung), KI-Antworten (Empfehlungstext)</li>
 *   <li>MQTT raus: KI-Auftraege, DeliveryMessages (Fitness abends, Empfehlung morgens)</li>
 * </ul>
 */
@Configuration
@Import(CyclingWebConfig.class)
public class TrainingConfig {

    @Bean
    public OpenAPI trainingOpenApi() {
        return CyclingWebConfig.openApi("Cycling Training API");
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

    /** Zeit und Zeitzone fuer "heute", Versandzeiten und Zeitplan. */
    @Bean
    public Clock trainingClock(@Value("${training.zone:Europe/Berlin}") String zone) {
        return Clock.system(parseZone(zone));
    }

    @Bean
    public IntervalsClient intervalsClient(@Value("${intervals-service.base-url}") String baseUrl,
                                           @Value("${intervals-service.connect-timeout:5s}") Duration connectTimeout,
                                           @Value("${intervals-service.read-timeout:60s}") Duration readTimeout) {
        return IntervalsClient.create(baseUrl, connectTimeout, readTimeout);
    }

    // ---- Fitness --------------------------------------------------------------------------------

    @Bean
    public FitnessMessageBuilder fitnessMessageBuilder(@Value("${fitness.targets:}") String targets) {
        return new FitnessMessageBuilder(DeliveryTargets.parse("fitness.targets", targets));
    }

    @Bean
    public FitnessSummaryJob fitnessSummaryJob(FitnessMessageBuilder messageBuilder, MqttMessaging messaging,
                                               Clock clock,
                                               @Value("${fitness.summary.time:21:00}") String time) {
        return new FitnessSummaryJob(messageBuilder, messaging, clock, parseTime("fitness.summary.time", time));
    }

    // ---- Tagesempfehlung ------------------------------------------------------------------------

    @Bean
    public IntervalsData intervalsData(IntervalsClient client) {
        return new IntervalsRestClient(client);
    }

    @Bean
    public RecommendationEngine recommendationEngine(IntervalsData intervals, MqttMessaging messaging, Clock clock,
                                                     @Value("${recommendation.text-timeout:90s}") Duration textTimeout) {
        return new RecommendationEngine(intervals, messaging, clock, textTimeout);
    }

    @Bean
    public RecommendationMessageBuilder recommendationMessageBuilder(@Value("${recommendation.targets:}") String targets) {
        return new RecommendationMessageBuilder(DeliveryTargets.parse("recommendation.targets", targets));
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
        FitnessSummaryJob fitness = context.getBean(FitnessSummaryJob.class);
        RecommendationEngine engine = context.getBean(RecommendationEngine.class);

        messaging.subscribe(Topics.INTERVALS_WELLNESS_UPDATED, WellnessUpdated.class, fitness::onWellness);
        messaging.subscribe(Topics.aiResult(RecommendationEngine.AI_SOURCE), AiResult.class, engine::applyText);
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
            throw new IllegalStateException("training.zone: '" + zone + "' ist keine Zeitzone (z. B. Europe/Berlin)", e);
        }
    }
}
