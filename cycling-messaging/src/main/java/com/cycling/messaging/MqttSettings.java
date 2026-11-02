package com.cycling.messaging;

import java.time.Duration;

/**
 * Verbindungsdaten zum Broker.
 *
 * @param clientId      muss pro Service fest und eindeutig sein, sonst geht die persistente Session verloren
 * @param sessionExpiry so lange haelt der Broker verpasste QoS-1-Nachrichten fuer einen offline Service vor
 */
public record MqttSettings(String brokerUrl, String clientId, String username, String password, Duration sessionExpiry) {

    public MqttSettings {
        if (brokerUrl == null || brokerUrl.isBlank()) {
            throw new IllegalArgumentException("MQTT broker-url fehlt");
        }
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("MQTT client-id fehlt");
        }
    }
}
