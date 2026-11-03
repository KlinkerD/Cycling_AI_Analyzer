package com.cycling.report;

import com.cycling.messaging.MessagePublisher;
import com.cycling.messaging.Topics;
import com.cycling.messaging.contract.DeliveryMessage;
import com.cycling.messaging.contract.ReportContent;
import com.cycling.messaging.contract.ReportDeliveryRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

/**
 * Verarbeitet Versandauftraege fuer Reports: Charts rendern, {@link DeliveryMessage} bauen und an den
 * delivery-service schicken. Der Report steckt vollstaendig im Auftrag, es gibt keinen Rueckruf beim
 * analysis-service. Die requestId wird zur messageId, dadurch verwirft der delivery-service doppelt
 * zugestellte Auftraege.
 * <p>
 * Kein eigener Speicher: schlaegt Rendern (QuickChart) oder Senden fehl, wird nur im Speicher nach den
 * konfigurierten Abstaenden wiederholt. Geht der Service dabei neu an, ist der Auftrag weg ("Erneut senden"
 * im Frontend).
 */
public class ReportRequestHandler {

    /** Fuehrt eine Aufgabe nach einer Verzoegerung aus; eigenes Interface, damit Tests nicht warten. */
    @FunctionalInterface
    public interface Scheduler {
        void schedule(Runnable task, Duration delay);
    }

    private static final Logger log = LoggerFactory.getLogger(ReportRequestHandler.class);

    private final ReportChartRenderer renderer;
    private final ReportMessageBuilder messageBuilder;
    private final MessagePublisher publisher;
    private final Scheduler scheduler;
    private final List<Duration> retryDelays;

    public ReportRequestHandler(ReportChartRenderer renderer, ReportMessageBuilder messageBuilder,
                                MessagePublisher publisher, Scheduler scheduler, List<Duration> retryDelays) {
        this.renderer = renderer;
        this.messageBuilder = messageBuilder;
        this.publisher = publisher;
        this.scheduler = scheduler;
        this.retryDelays = List.copyOf(retryDelays);
    }

    /** Nimmt den Auftrag an und arbeitet ihn im Hintergrund ab (der MQTT-Worker wartet nicht auf QuickChart). */
    public void handle(ReportDeliveryRequest request) {
        ReportContent report = request.report();
        if (report == null || report.analysis() == null || report.streams() == null) {
            // z. B. ein Auftrag im alten Format, der noch in der Broker-Session lag
            log.error("Versandauftrag {} fuer Aktivitaet {} ohne vollstaendigen Report verworfen",
                    request.requestId(), request.activityId());
            return;
        }
        log.info("Versandauftrag {} fuer Aktivitaet {} angenommen", request.requestId(), request.activityId());
        scheduler.schedule(() -> attempt(request, 0), Duration.ZERO);
    }

    private void attempt(ReportDeliveryRequest request, int failures) {
        try {
            ReportCharts charts = renderer.render(request.report());
            DeliveryMessage message = messageBuilder.build(request.requestId(), request.report(), charts);
            publisher.publish(Topics.DELIVERY_REQUEST_REPORT, message);
            log.info("Report {} als Nachricht {} an den delivery-service uebergeben ({} Anhaenge)",
                    request.activityId(), request.requestId(), message.attachments().size());
        } catch (RuntimeException e) {
            if (failures < retryDelays.size()) {
                Duration delay = retryDelays.get(failures);
                log.warn("Report {} (Auftrag {}) fehlgeschlagen (Versuch {}/{}), neuer Versuch in {}: {}",
                        request.activityId(), request.requestId(), failures + 1, retryDelays.size() + 1,
                        delay, e.getMessage());
                scheduler.schedule(() -> attempt(request, failures + 1), delay);
            } else {
                log.error("Report {} (Auftrag {}) endgueltig aufgegeben nach {} Versuchen: {}",
                        request.activityId(), request.requestId(), failures + 1, e.getMessage(), e);
            }
        }
    }
}
