package com.cycling.delivery;

import com.cycling.dto.ReportDto;
import com.cycling.service.AnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Einstiegspunkt fuer den Report-Versand. Reiht synchron ein (damit der Aufrufer die Kanaele kennt)
 * und sendet im Hintergrund. Der Executor ist single-threaded, dadurch laufen Sofortversand,
 * Retry und manuelles Senden nie parallel.
 */
public class ReportDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ReportDispatcher.class);

    private final ReportDeliveryService deliveryService;
    private final AnalysisService analysisService;
    private final Executor executor;

    public ReportDispatcher(ReportDeliveryService deliveryService, AnalysisService analysisService, Executor executor) {
        this.deliveryService = deliveryService;
        this.analysisService = analysisService;
        this.executor = executor;
    }

    /** Reiht den Report fuer alle aktiven Kanaele ein und sendet ihn im Hintergrund. */
    public List<DeliveryChannel> dispatch(ReportDto report) {
        List<DeliveryChannel> channels = deliveryService.enqueue(report.analysis().getActivityId());
        if (!channels.isEmpty()) {
            executor.execute(() -> runSafely("Versand", () -> deliveryService.deliver(report)));
        }
        return channels;
    }

    /** Manuelles Senden eines gespeicherten Reports an alle aktiven Kanaele. */
    public List<DeliveryChannel> resend(String activityId) {
        if (deliveryService.enabledChannels().isEmpty()) {
            return List.of();
        }
        return dispatch(analysisService.getReport(activityId));
    }

    /** Alle 15 Minuten offene Versaende (max. 2 Tage alt) erneut versuchen. */
    @Scheduled(fixedDelay = 15 * 60 * 1000, initialDelay = 2 * 60 * 1000)
    public void retryPending() {
        executor.execute(() -> runSafely("Retry", () -> deliveryService.retryPending(analysisService::getReport)));
    }

    private static void runSafely(String what, Runnable task) {
        try {
            task.run();
        } catch (RuntimeException e) {
            log.error("Report-{} fehlgeschlagen: {}", what, e.getMessage(), e);
        }
    }
}
