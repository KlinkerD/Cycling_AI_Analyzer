package com.cycling.recommendation;

import com.cycling.messaging.MessagePublisher;
import com.cycling.messaging.Topics;
import com.cycling.messaging.contract.DeliveryMessage;
import com.cycling.messaging.contract.RecommendationUpdated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.function.Consumer;

/**
 * Schickt einmal am Tag ab der Versandzeit (Default 09:00) die Tagesempfehlung an den delivery-service.
 *
 * <p>Ab der Versandzeit wird frisch gerechnet. Gesendet wird, sobald der KI-Text da ist, spaetestens
 * nach {@code textWait} mit dem Fallback-Text. Geprueft wird jede Minute; scheitert die Berechnung
 * (intervals-service weg) oder die Uebergabe, kommt der naechste Versuch eine Minute spaeter.
 * Begonnen wird nur bis {@code catchUpUntil} (Default 12:00), danach gilt der Tag als verpasst -
 * so holt ein Neustart um 10:30 nach, einer um 15:00 nicht.
 *
 * <p>Doppelt wird nie zugestellt: die messageId haengt am Datum, der delivery-service verwirft sie beim
 * zweiten Mal. Die Engine wird nie unter dem Lock dieses Jobs aufgerufen (sie ruft {@link #accept} zurueck).
 */
public class DailyRecommendationJob implements Consumer<RecommendationUpdated> {

    private static final Logger log = LoggerFactory.getLogger(DailyRecommendationJob.class);
    /** cycling/delivery/request/recommendation */
    static final String TOPIC = Topics.deliveryRequest("recommendation");
    static final String REQUEST_ID_PREFIX = "daily-";

    private final RecommendationEngine engine;
    private final RecommendationMessageBuilder messageBuilder;
    private final MessagePublisher publisher;
    private final Clock clock;
    private final LocalTime sendAt;
    private final LocalTime catchUpUntil;
    private final Duration textWait;

    /** Tag, fuer den die Empfehlung schon uebergeben wurde. */
    private LocalDate sentFor;
    /** Laufende Berechnung fuer den Versand; null = keine. */
    private String requestId;
    private LocalDate requestedFor;
    private Instant startedAt;
    /** Letzte Nachricht dieser Berechnung. */
    private RecommendationUpdated latest;

    /** @param clock liefert Zeit und Zeitzone, in der Versandzeit und "heute" gelten */
    public DailyRecommendationJob(RecommendationEngine engine, RecommendationMessageBuilder messageBuilder,
                                  MessagePublisher publisher, Clock clock, LocalTime sendAt,
                                  LocalTime catchUpUntil, Duration textWait) {
        if (!catchUpUntil.isAfter(sendAt)) {
            throw new IllegalArgumentException("Nachholen bis " + catchUpUntil + " muss nach der Versandzeit " + sendAt + " liegen");
        }
        this.engine = engine;
        this.messageBuilder = messageBuilder;
        this.publisher = publisher;
        this.clock = clock;
        this.sendAt = sendAt;
        this.catchUpUntil = catchUpUntil;
        this.textWait = textWait;
    }

    /** Jede veroeffentlichte Empfehlung; relevant ist nur die eigene Berechnung. */
    @Override
    public void accept(RecommendationUpdated update) {
        boolean done;
        synchronized (this) {
            if (requestId == null || !requestId.equals(update.requestId())) return;
            latest = update;
            done = !update.textPending();
        }
        if (done) check();
    }

    @Scheduled(fixedDelayString = "${recommendation.daily.check-interval:1m}",
            initialDelayString = "${recommendation.daily.initial-delay:1m}")
    public void check() {
        String toCompute;
        synchronized (this) {
            ZonedDateTime now = ZonedDateTime.now(clock);
            LocalDate today = now.toLocalDate();
            if (today.equals(sentFor)) return;
            if (requestId != null && !today.equals(requestedFor)) {
                requestId = null; // Berechnung von gestern
            }
            if (requestId != null) {
                boolean waitedEnough = !clock.instant().isBefore(startedAt.plus(textWait));
                if (latest != null && (!latest.textPending() || waitedEnough)) {
                    send(latest, today);
                } else if (latest == null && waitedEnough) {
                    requestId = null; // nichts angekommen, naechste Minute neu rechnen
                }
                return;
            }
            LocalTime time = now.toLocalTime();
            if (time.isBefore(sendAt) || !time.isBefore(catchUpUntil)) return;

            toCompute = REQUEST_ID_PREFIX + today + "-" + now.toLocalTime().withNano(0).toString().replace(":", "");
            requestId = toCompute;
            requestedFor = today;
            startedAt = clock.instant();
            latest = null;
        }
        try {
            engine.compute(toCompute);
        } catch (RuntimeException e) {
            log.warn("Tagesempfehlung fuer den Versand nicht berechnet, naechster Versuch in einer Minute: {}",
                    e.getMessage());
            synchronized (this) {
                if (toCompute.equals(requestId)) requestId = null;
            }
        }
    }

    /** Nur unter dem Lock aufrufen. */
    private void send(RecommendationUpdated update, LocalDate today) {
        DeliveryMessage message = messageBuilder.build(update, today);
        try {
            publisher.publish(TOPIC, message);
            sentFor = today;
            requestId = null;
            log.info("Tagesempfehlung {} ({}) an den delivery-service uebergeben{}", message.messageId(),
                    update.category(), update.fallbackUsed() ? ", ohne KI-Text" : "");
        } catch (RuntimeException e) {
            log.warn("Tagesempfehlung {} nicht uebergeben, naechster Versuch in einer Minute: {}",
                    message.messageId(), e.getMessage());
        }
    }
}
