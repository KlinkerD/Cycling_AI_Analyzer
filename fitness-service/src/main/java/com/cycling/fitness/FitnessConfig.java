package com.cycling.fitness;

import com.cycling.messaging.MqttMessaging;
import com.cycling.messaging.MqttSettings;
import com.cycling.messaging.Topics;
import com.cycling.messaging.contract.WellnessUpdated;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;

/** Verdrahtet den fitness-service: MQTT rein (WellnessUpdated), MQTT raus (DeliveryMessage). */
@Configuration
public class FitnessConfig {

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
    public FitnessMessageBuilder fitnessMessageBuilder(@Value("${fitness.targets:}") String targets) {
        return new FitnessMessageBuilder(FitnessTargets.parse(targets));
    }

    @Bean
    public FitnessSummaryJob fitnessSummaryJob(FitnessMessageBuilder messageBuilder, MqttMessaging messaging,
                                               @Value("${fitness.summary.time:21:00}") String time,
                                               @Value("${fitness.summary.zone:Europe/Berlin}") String zone) {
        return new FitnessSummaryJob(messageBuilder, messaging, Clock.system(parseZone(zone)), parseTime(time));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void connectMqtt(ApplicationReadyEvent event) {
        var context = event.getApplicationContext();
        MqttMessaging messaging = context.getBean(MqttMessaging.class);
        FitnessSummaryJob job = context.getBean(FitnessSummaryJob.class);

        messaging.subscribe(Topics.INTERVALS_WELLNESS_UPDATED, WellnessUpdated.class, job::onWellness);
        messaging.start();
    }

    static LocalTime parseTime(String time) {
        try {
            return LocalTime.parse(time.trim());
        } catch (RuntimeException e) {
            throw new IllegalStateException("fitness.summary.time: '" + time + "' ist keine Uhrzeit, erwartet HH:mm (z. B. 21:00)", e);
        }
    }

    static ZoneId parseZone(String zone) {
        try {
            return ZoneId.of(zone.trim());
        } catch (RuntimeException e) {
            throw new IllegalStateException("fitness.summary.zone: '" + zone + "' ist keine Zeitzone (z. B. Europe/Berlin)", e);
        }
    }
}
