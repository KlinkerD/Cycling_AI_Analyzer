package com.cycling.messaging;

/** Veroeffentlicht eine Nachricht (wird als JSON mit QoS 1 gesendet). */
public interface MessagePublisher {

    /**
     * @throws MessagingException wenn die Nachricht nicht an den Broker uebergeben werden konnte
     *                            (z. B. noch nie verbunden gewesen)
     */
    void publish(String topic, Object message);
}
