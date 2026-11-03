package com.cycling.delivery;

import com.cycling.messaging.Topics;
import com.cycling.messaging.contract.DeliveryMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Einstiegspunkt fuer Versandauftraege. Nimmt synchron an (auf dem MQTT-Worker, damit eine
 * Neuzustellung sicher erkannt wird) und sendet im Hintergrund. Der Executor ist single-threaded,
 * dadurch laufen Sofortversand, Retry und Aufraeumen nie parallel.
 */
public class DeliveryDispatcher {

    private static final Logger log = LoggerFactory.getLogger(DeliveryDispatcher.class);

    private final DeliveryService deliveryService;
    private final Executor executor;

    public DeliveryDispatcher(DeliveryService deliveryService, Executor executor) {
        this.deliveryService = deliveryService;
        this.executor = executor;
    }

    /** Verarbeitet eine Nachricht von {@code cycling/delivery/request/<quelle>}. */
    public void handle(String topic, DeliveryMessage message) {
        String source = Topics.deliverySource(topic);
        List<Delivery> pending = deliveryService.accept(source, message);
        if (pending.isEmpty()) {
            log.info("Nachricht {} von {}: nichts zu senden", message.messageId(), source);
            return;
        }
        executor.execute(() -> runSafely("Versand", () -> deliveryService.deliverPending(message.messageId())));
    }

    /** Alle 15 Minuten offene Versaende (max. 2 Tage alt) erneut versuchen. */
    @Scheduled(fixedDelay = 15 * 60 * 1000, initialDelay = 2 * 60 * 1000)
    public void retryPending() {
        executor.execute(() -> runSafely("Retry", deliveryService::retryPending));
    }

    /** Alle 6 Stunden alte Inhalte und Statuszeilen loeschen. */
    @Scheduled(fixedDelay = 6 * 60 * 60 * 1000, initialDelay = 5 * 60 * 1000)
    public void cleanUp() {
        executor.execute(() -> runSafely("Aufraeumen", deliveryService::cleanUp));
    }

    private static void runSafely(String what, Runnable task) {
        try {
            task.run();
        } catch (RuntimeException e) {
            log.error("{} fehlgeschlagen: {}", what, e.getMessage(), e);
        }
    }
}
