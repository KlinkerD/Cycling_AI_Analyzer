package com.cycling.messaging.contract;

import java.util.List;

/**
 * Zustand des delivery-service (retained auf {@code Topics.DELIVERY_STATUS}).
 *
 * @param channels aktive Versandkanaele, z. B. ["MAIL", "DISCORD"]; leer = Versand abgeschaltet
 */
public record DeliveryServiceStatus(boolean online, List<String> channels) {
}
