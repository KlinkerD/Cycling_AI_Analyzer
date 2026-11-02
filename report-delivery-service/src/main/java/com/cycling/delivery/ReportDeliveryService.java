package com.cycling.delivery;

import com.cycling.delivery.report.ReportDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Verwaltet den Versand von Reports ueber alle Kanaele inkl. gespeicherter Wiederholung.
 * Pro (Aktivitaet, Kanal) gibt es eine Zeile in report_delivery.
 */
public class ReportDeliveryService {

    /** Erster Versand + 4 Wiederholungen (bei 15-Minuten-Takt also rund 1 Stunde). */
    static final int MAX_ATTEMPTS = 5;
    /** Aeltere offene Zeilen werden nicht mehr automatisch nachgesendet. */
    static final Duration RETRY_WINDOW = Duration.ofDays(2);
    private static final Logger log = LoggerFactory.getLogger(ReportDeliveryService.class);

    private final List<ReportSender> senders;
    private final ReportDeliveryRepository repository;
    private final ReportChartRenderer renderer;
    private final Clock clock;

    public ReportDeliveryService(List<ReportSender> senders, ReportDeliveryRepository repository,
                                 ReportChartRenderer renderer, Clock clock) {
        this.senders = senders;
        this.repository = repository;
        this.renderer = renderer;
        this.clock = clock;
    }

    /** Kanaele, die per Config eingeschaltet sind. */
    public List<DeliveryChannel> enabledChannels() {
        return senders.stream().filter(ReportSender::isEnabled).map(ReportSender::channel).toList();
    }

    /**
     * Reiht den Report fuer alle aktiven Kanaele (neu) ein. Kanaele, die derselbe Auftrag schon
     * eingereiht hat (MQTT-Neuzustellung), bleiben unveraendert. Liefert die neu eingereihten Kanaele.
     */
    public List<DeliveryChannel> enqueue(String activityId, String requestId) {
        List<DeliveryChannel> channels = new ArrayList<>();
        for (ReportSender sender : senders) {
            if (!sender.isEnabled()) continue;
            ReportDelivery delivery = repository.findByActivityIdAndChannel(activityId, sender.channel())
                    .orElseGet(() -> new ReportDelivery(activityId, sender.channel()));
            if (requestId != null && requestId.equals(delivery.getRequestId())) {
                log.info("Versandauftrag {} fuer {} per {} bereits bekannt - kein zweiter Versand",
                        requestId, activityId, sender.channel());
                continue;
            }
            delivery.requeue(LocalDateTime.now(clock), requestId);
            repository.save(delivery);
            channels.add(sender.channel());
        }
        return channels;
    }

    /** Sendet alle offenen Zeilen dieses Reports. */
    public void deliver(ReportDto report) {
        String activityId = report.analysis().getActivityId();
        List<ReportDelivery> pending = pendingForEnabledChannels(activityId);
        if (pending.isEmpty()) return;

        // Charts nur einmal rendern, fuer alle Kanaele gemeinsam.
        ReportCharts charts = renderer.render(report);
        for (ReportDelivery delivery : pending) {
            attempt(enabledSender(delivery.getChannel()), report, charts, delivery);
        }
    }

    /** Wird vom Scheduler aufgerufen: offene Zeilen erneut versuchen. */
    public void retryPending(Function<String, ReportDto> reportLoader) {
        LocalDateTime cutoff = LocalDateTime.now(clock).minus(RETRY_WINDOW);
        List<String> activityIds = repository.findByStatusAndQueuedAtAfter(DeliveryStatus.PENDING, cutoff).stream()
                .map(ReportDelivery::getActivityId)
                .distinct()
                .toList();
        for (String activityId : activityIds) {
            deliverPending(activityId, reportLoader);
        }
    }

    /**
     * Laedt den Report nur, wenn fuer die Aktivitaet noch etwas offen ist, und sendet ihn.
     * Ist der Report nicht ladbar, zaehlt das fuer jede offene Zeile als voruebergehender Fehlversuch.
     */
    public void deliverPending(String activityId, Function<String, ReportDto> reportLoader) {
        if (pendingForEnabledChannels(activityId).isEmpty()) return;
        ReportDto report;
        try {
            report = reportLoader.apply(activityId);
        } catch (RuntimeException e) {
            markLoadFailure(activityId, "Report konnte nicht geladen werden: " + e.getMessage());
            return;
        }
        deliver(report);
    }

    /** Report nicht ladbar: zaehlt fuer jede offene Zeile (aktiver Kanaele) als voruebergehender Fehlversuch. */
    private void markLoadFailure(String activityId, String error) {
        for (ReportDelivery delivery : pendingForEnabledChannels(activityId)) {
            startAttempt(delivery);
            recordFailure(delivery, error, false);
            repository.save(delivery);
        }
    }

    private void attempt(ReportSender sender, ReportDto report, ReportCharts charts, ReportDelivery delivery) {
        startAttempt(delivery);
        try {
            sender.send(report, charts, delivery);
            delivery.setStatus(DeliveryStatus.SENT);
            delivery.setLastError(null);
            log.info("Report {} per {} versendet.", delivery.getActivityId(), delivery.getChannel());
        } catch (DeliveryException e) {
            recordFailure(delivery, e.getMessage(), e.isPermanent());
        } catch (RuntimeException e) {
            // Unerwarteter Fehler: wie voruebergehend behandeln, andere Kanaele laufen weiter.
            recordFailure(delivery, e.getClass().getSimpleName() + ": " + e.getMessage(), false);
        }
        repository.save(delivery);
    }

    private void startAttempt(ReportDelivery delivery) {
        delivery.setAttempts(delivery.getAttempts() + 1);
        delivery.setLastAttemptAt(LocalDateTime.now(clock));
    }

    /** Offene Zeilen; die abgeschalteter Kanaele bleiben PENDING, bis der Kanal wieder aktiv ist. */
    private List<ReportDelivery> pendingForEnabledChannels(String activityId) {
        return repository.findByActivityIdAndStatus(activityId, DeliveryStatus.PENDING).stream()
                .filter(d -> enabledSender(d.getChannel()) != null)
                .toList();
    }

    private void recordFailure(ReportDelivery delivery, String error, boolean permanent) {
        delivery.setLastError(error);
        if (permanent || delivery.getAttempts() >= MAX_ATTEMPTS) {
            delivery.setStatus(DeliveryStatus.FAILED);
            log.error("Report {} per {} endgueltig fehlgeschlagen nach {} Versuch(en): {}",
                    delivery.getActivityId(), delivery.getChannel(), delivery.getAttempts(), error);
        } else {
            log.warn("Report {} per {} fehlgeschlagen (Versuch {}/{}), neuer Versuch spaeter: {}",
                    delivery.getActivityId(), delivery.getChannel(), delivery.getAttempts(), MAX_ATTEMPTS, error);
        }
    }

    private ReportSender enabledSender(DeliveryChannel channel) {
        return senders.stream().filter(s -> s.channel() == channel && s.isEnabled()).findFirst().orElse(null);
    }
}
