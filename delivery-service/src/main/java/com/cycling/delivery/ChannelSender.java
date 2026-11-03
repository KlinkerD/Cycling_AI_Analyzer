package com.cycling.delivery;

import com.cycling.messaging.contract.DeliveryMessage;

import java.util.Set;

/** Ein Versandkanal (Mail, Discord, ...). */
public interface ChannelSender {

    DeliveryChannel channel();

    boolean isEnabled();

    /** Logische Zielnamen, die dieser Kanal kennt (z. B. "default", "reports"). */
    Set<String> destinations();

    /**
     * Sendet die Nachricht an das Ziel. Kanaele mit mehreren Teilen (Discord: Text + Anhaenge) lesen
     * {@link Delivery#getCompletedParts()} und melden fertige Teile ueber
     * {@link Delivery#markPartCompleted()}, damit ein Retry nur den Rest sendet.
     */
    void send(DeliveryMessage message, String destination, Delivery delivery) throws DeliveryException;
}
