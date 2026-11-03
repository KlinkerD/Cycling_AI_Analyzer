package com.cycling.service;

import com.cycling.dto.ReportDto;
import com.cycling.messaging.MessagePublisher;
import com.cycling.messaging.Topics;
import com.cycling.messaging.contract.DeliveryServiceStatus;
import com.cycling.messaging.contract.ReportDeliveryRequest;
import com.cycling.repository.ActivityAnalysisRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * "Erneut senden"-Button: laedt den gespeicherten Report und schickt ihn als Versandauftrag an den
 * report-service. Welche Kanaele aktiv sind, kennt das Backend aus der retained Status-Nachricht
 * des delivery-service, damit der Button sofort antworten kann.
 */
@Service
public class ReportResendService {

    private static final Logger log = LoggerFactory.getLogger(ReportResendService.class);

    private final ActivityAnalysisRepository repository;
    private final AnalysisService analysisService;
    private final MessagePublisher publisher;
    private final AtomicReference<DeliveryServiceStatus> status = new AtomicReference<>();

    public ReportResendService(ActivityAnalysisRepository repository, AnalysisService analysisService,
                               MessagePublisher publisher) {
        this.repository = repository;
        this.analysisService = analysisService;
        this.publisher = publisher;
    }

    public void updateStatus(DeliveryServiceStatus newStatus) {
        log.info("delivery-service {}: Kanaele {}", newStatus.online() ? "online" : "offline", newStatus.channels());
        status.set(newStatus);
    }

    public Outcome resend(String activityId) {
        if (repository.findByActivityId(activityId).isEmpty()) {
            return new Outcome(Result.NOT_FOUND, List.of());
        }
        DeliveryServiceStatus current = status.get();
        if (current == null) {
            return new Outcome(Result.UNAVAILABLE, List.of());
        }
        if (current.channels() == null || current.channels().isEmpty()) {
            return new Outcome(Result.DISABLED, List.of());
        }

        ReportDto report;
        try {
            report = analysisService.getReport(activityId);
        } catch (ReportNotFoundException e) {
            return new Outcome(Result.NOT_FOUND, List.of());
        } catch (RuntimeException e) {
            log.warn("Report {} fuer erneuten Versand nicht ladbar: {}", activityId, e.getMessage());
            return new Outcome(Result.REPORT_UNAVAILABLE, List.of());
        }

        try {
            // Ist der Service gerade offline, haelt der Broker den Auftrag in seiner Session vor.
            publisher.publish(Topics.REPORT_RESEND, new ReportDeliveryRequest(
                    UUID.randomUUID().toString(), activityId, ReportContentMapper.toContent(report)));
        } catch (RuntimeException e) {
            log.warn("Versandauftrag fuer {} nicht gesendet: {}", activityId, e.getMessage());
            return new Outcome(Result.UNAVAILABLE, List.of());
        }
        return new Outcome(Result.QUEUED, current.channels());
    }

    /**
     * {@code UNAVAILABLE}: Versand-Service unbekannt oder Broker weg; {@code REPORT_UNAVAILABLE}: der Report
     * selbst liess sich nicht laden (z. B. intervals-service nicht erreichbar).
     */
    public enum Result { QUEUED, DISABLED, NOT_FOUND, UNAVAILABLE, REPORT_UNAVAILABLE }

    public record Outcome(Result result, List<String> channels) {
    }
}
