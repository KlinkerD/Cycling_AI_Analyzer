package com.cycling.delivery;

import com.cycling.messaging.MqttMessaging;
import com.cycling.messaging.MqttSettings;
import com.cycling.messaging.Topics;
import com.cycling.messaging.contract.DeliveryMessage;
import com.cycling.messaging.contract.DeliveryServiceStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.time.Duration;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * MQTT-Anbindung: nimmt Versandauftraege aller Quellen ({@code cycling/delivery/request/+}) entgegen
 * und meldet die aktiven Kanaele retained, damit der analysis-service beim "Erneut senden" sofort
 * antworten kann.
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
        DeliveryDispatcher dispatcher = context.getBean(DeliveryDispatcher.class);
        List<String> channels = context.getBean(DeliveryService.class).enabledChannels().stream()
                .map(Enum::name)
                .toList();

        BiConsumer<String, DeliveryMessage> handler = dispatcher::handle;
        messaging.subscribe(Topics.DELIVERY_REQUEST_ALL, DeliveryMessage.class, handler);
        messaging.setWill(Topics.DELIVERY_STATUS, new DeliveryServiceStatus(false, channels));
        messaging.onConnect(() -> messaging.publishRetained(Topics.DELIVERY_STATUS,
                new DeliveryServiceStatus(true, channels)));
        messaging.start();
    }
}
