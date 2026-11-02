package com.cycling.delivery;

import com.cycling.delivery.report.ReportDto;
import com.cycling.messaging.contract.ReportDeliveryRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * Einstiegspunkt fuer den Report-Versand. Reiht synchron ein (auf dem MQTT-Worker, damit eine
 * Neuzustellung sicher erkannt wird) und sendet im Hintergrund. Der Executor ist single-threaded,
 * dadurch laufen Sofortversand, Retry und manuelles Senden nie parallel.
 */
public class ReportDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ReportDispatcher.class);

    private final ReportDeliveryService deliveryService;
    private final Function<String, ReportDto> reportLoader;
    private final Executor executor;

    public ReportDispatcher(ReportDeliveryService deliveryService, Function<String, ReportDto> reportLoader,
                            Executor executor) {
        this.deliveryService = deliveryService;
        this.reportLoader = reportLoader;
        this.executor = executor;
    }

    /** Verarbeitet einen Versandauftrag (neue Analyse oder "erneut senden"). Liefert die eingereihten Kanaele. */
    public List<DeliveryChannel> handle(ReportDeliveryRequest request) {
        List<DeliveryChannel> channels = deliveryService.enqueue(request.activityId(), request.requestId());
        if (channels.isEmpty()) {
            log.info("Versandauftrag {} fuer {}: nichts zu tun", request.requestId(), request.activityId());
            return channels;
        }
        log.info("Versandauftrag {} fuer {} eingereiht: {}", request.requestId(), request.activityId(), channels);
        executor.execute(() -> runSafely("Versand",
                () -> deliveryService.deliverPending(request.activityId(), reportLoader)));
        return channels;
    }

    /** Alle 15 Minuten offene Versaende (max. 2 Tage alt) erneut versuchen. */
    @Scheduled(fixedDelay = 15 * 60 * 1000, initialDelay = 2 * 60 * 1000)
    public void retryPending() {
        executor.execute(() -> runSafely("Retry", () -> deliveryService.retryPending(reportLoader)));
    }

    private static void runSafely(String what, Runnable task) {
        try {
            task.run();
        } catch (RuntimeException e) {
            log.error("Report-{} fehlgeschlagen: {}", what, e.getMessage(), e);
        }
    }
}
