package com.cycling.training.fitness;

import com.cycling.messaging.MessagePublisher;
import com.cycling.messaging.Topics;
import com.cycling.messaging.contract.DeliveryMessage;
import com.cycling.messaging.contract.WellnessUpdated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;

/**
 * Schickt einmal am Tag ab der Versandzeit (Default 21:00) die Tageszusammenfassung.
 * <p>
 * Der letzte {@link WellnessUpdated} liegt nur im Speicher; nach einem Start liefert der Broker die
 * retained Nachricht sofort. Gesendet wird, sobald alles zusammenpasst: Versandzeit erreicht, Stand
 * ist von heute, fuer heute noch nichts gesendet. Geprueft wird jede Minute und bei jedem
 * eingehenden Stand. Damit sind puenktlicher Versand, verspaetete Daten und ein Neustart nach der
 * Versandzeit derselbe Weg; nachgeholt wird bis Mitternacht.
 * <p>
 * Doppelt wird nie zugestellt: die messageId haengt am Datum, der delivery-service verwirft sie beim
 * zweiten Mal (z. B. nach einem Neustart um 21:30).
 */
public class FitnessSummaryJob {

    private static final Logger log = LoggerFactory.getLogger(FitnessSummaryJob.class);
    /** cycling/delivery/request/fitness */
    static final String TOPIC = Topics.deliveryRequest("fitness");

    private final FitnessMessageBuilder messageBuilder;
    private final MessagePublisher publisher;
    private final Clock clock;
    private final LocalTime sendAt;

    /** Letzter empfangener Stand. Zugriff nur synchronisiert (MQTT-Worker und Scheduler). */
    private WellnessUpdated latest;
    /** Tag, fuer den die Zusammenfassung schon uebergeben wurde. */
    private LocalDate sentFor;
    /** Tag, fuer den schon einmal "kein aktueller Stand" geloggt wurde. */
    private LocalDate warnedFor;

    /** @param clock liefert Zeit und Zeitzone, in der Versandzeit und "heute" gelten */
    public FitnessSummaryJob(FitnessMessageBuilder messageBuilder, MessagePublisher publisher, Clock clock,
                             LocalTime sendAt) {
        this.messageBuilder = messageBuilder;
        this.publisher = publisher;
        this.clock = clock;
        this.sendAt = sendAt;
    }

    /** Neuer Stand vom intervals-service. */
    public synchronized void onWellness(WellnessUpdated update) {
        if (update == null || update.today() == null || update.today().date() == null) {
            log.warn("WellnessUpdated ohne Werte fuer heute ignoriert");
            return;
        }
        latest = update;
        log.debug("Fitness-Stand vom {} empfangen", update.today().date());
        check();
    }

    @Scheduled(fixedDelayString = "${fitness.summary.check-interval:1m}",
            initialDelayString = "${fitness.summary.initial-delay:1m}")
    public synchronized void check() {
        ZonedDateTime now = ZonedDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        if (now.toLocalTime().isBefore(sendAt) || today.equals(sentFor)) return;

        if (latest == null || !today.toString().equals(latest.today().date())) {
            if (!today.equals(warnedFor)) {
                log.warn("Kein Fitness-Stand von heute ({}) - Zusammenfassung wartet (letzter Stand: {})",
                        today, latest == null ? "keiner" : latest.today().date());
                warnedFor = today;
            }
            return;
        }

        DeliveryMessage message = messageBuilder.build(latest);
        try {
            publisher.publish(TOPIC, message);
            sentFor = today;
            log.info("Fitness-Zusammenfassung {} an den delivery-service uebergeben", message.messageId());
        } catch (RuntimeException e) {
            log.warn("Fitness-Zusammenfassung {} nicht uebergeben, naechster Versuch in einer Minute: {}",
                    message.messageId(), e.getMessage());
        }
    }
}
