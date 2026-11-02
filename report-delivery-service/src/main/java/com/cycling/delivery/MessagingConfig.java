package com.cycling.delivery;

import com.cycling.messaging.MqttMessaging;
import com.cycling.messaging.MqttSettings;
import com.cycling.messaging.Topics;
import com.cycling.messaging.contract.DeliveryServiceStatus;
import com.cycling.messaging.contract.ReportDeliveryRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.time.Duration;
import java.util.List;

/**
 * MQTT-Anbindung: nimmt Versandauftraege entgegen und meldet die aktiven Kanaele retained,
 * damit der analysis-service beim "Erneut senden" sofort antworten kann.
 */
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

    @EventListener(ApplicationReadyEvent.class)
    public void connectMqtt(ApplicationReadyEvent event) {
        var context = event.getApplicationContext();
        MqttMessaging messaging = context.getBean(MqttMessaging.class);
        ReportDispatcher dispatcher = context.getBean(ReportDispatcher.class);
        List<String> channels = context.getBean(ReportDeliveryService.class).enabledChannels().stream()
                .map(Enum::name)
                .toList();

        messaging.subscribe(Topics.REPORT_READY, ReportDeliveryRequest.class, dispatcher::handle);
        messaging.subscribe(Topics.REPORT_RESEND, ReportDeliveryRequest.class, dispatcher::handle);
        messaging.setWill(Topics.DELIVERY_STATUS, new DeliveryServiceStatus(false, channels));
        messaging.onConnect(() -> messaging.publishRetained(Topics.DELIVERY_STATUS,
                new DeliveryServiceStatus(true, channels)));
        messaging.start();
    }
}
