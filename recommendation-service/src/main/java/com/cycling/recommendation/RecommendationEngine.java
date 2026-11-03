package com.cycling.recommendation;

import com.cycling.intervals.api.IntervalsActivity;
import com.cycling.messaging.MessagePublisher;
import com.cycling.messaging.Topics;
import com.cycling.messaging.contract.RecommendationRefreshRequest;
import com.cycling.messaging.contract.RecommendationTextRequest;
import com.cycling.messaging.contract.RecommendationTextResult;
import com.cycling.messaging.contract.RecommendationUpdated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Berechnet die Tagesempfehlung und veroeffentlicht sie retained auf {@link Topics#RECOMMENDATION_UPDATED}.
 *
 * <p>Pro Berechnung zwei Nachrichten mit derselben requestId: sofort mit Fallback-Text
 * ({@code textPending=true}), dann mit KI-Text. Kommt kein KI-Text (Fehler, ai-service weg,
 * {@code textTimeout} abgelaufen), geht die zweite Nachricht mit dem Fallback raus. Ohne Daten gibt es
 * nur eine Nachricht ({@code INSUFFICIENT_DATA}, nichts mehr erwartet).
 *
 * <p>Ist der intervals-service nicht erreichbar, wird nichts veroeffentlicht: der letzte Stand bleibt.
 *
 * <p>Locks: Berechnungen laufen nacheinander ({@code computeLock}); der Zustand des offenen KI-Auftrags
 * liegt unter {@code stateLock}. Listener werden nie unter {@code stateLock} aufgerufen.
 */
public class RecommendationEngine {

    private static final Logger log = LoggerFactory.getLogger(RecommendationEngine.class);

    private final IntervalsData intervals;
    private final MessagePublisher publisher;
    private final Clock clock;
    private final Duration textTimeout;
    private final List<Consumer<RecommendationUpdated>> listeners = new CopyOnWriteArrayList<>();

    private final Object computeLock = new Object();
    private final Object stateLock = new Object();
    /** Erste Nachricht der Berechnung, deren KI-Text noch aussteht; null = nichts offen. */
    private RecommendationUpdated pending;
    private Instant pendingSince;

    /** @param textTimeout so lange wird hoechstens auf den KI-Text gewartet */
    public RecommendationEngine(IntervalsData intervals, MessagePublisher publisher, Clock clock, Duration textTimeout) {
        this.intervals = intervals;
        this.publisher = publisher;
        this.clock = clock;
        this.textTimeout = textTimeout;
    }

    /** Bekommt jede veroeffentlichte Empfehlung (beide Nachrichten), z. B. fuer den Tagesversand. */
    public void addListener(Consumer<RecommendationUpdated> listener) {
        listeners.add(listener);
    }

    /** Refresh-Auftrag vom backend. Fehler werden geloggt, das backend faellt nach seinem Timeout zurueck. */
    public void onRefresh(RecommendationRefreshRequest request) {
        String requestId = request.requestId() == null || request.requestId().isBlank()
                ? UUID.randomUUID().toString() : request.requestId();
        computeQuietly(requestId, "Refresh");
    }

    /** Zeitplan (alle paar Stunden). */
    public void computeScheduled() {
        computeQuietly(UUID.randomUUID().toString(), "Zeitplan");
    }

    private void computeQuietly(String requestId, String trigger) {
        try {
            compute(requestId);
        } catch (RuntimeException e) {
            log.warn("Tagesempfehlung ({}) nicht berechnet, letzter Stand bleibt: {}", trigger, e.getMessage());
        }
    }

    /**
     * Berechnet neu und veroeffentlicht die erste Nachricht.
     *
     * @return die erste Nachricht dieser Berechnung
     * @throws IntervalsUnavailableException wenn keine Wellness-Daten geladen werden konnten
     */
    public RecommendationUpdated compute(String requestId) {
        synchronized (computeLock) {
            LocalDate today = LocalDate.now(clock);
            Assessment assessment = RecommendationCalculator.assess(
                    intervals.wellness(today.minusDays(RecommendationCalculator.WELLNESS_DAYS), today));
            LocalDateTime now = LocalDateTime.now(clock);
            String fallback = RecommendationCalculator.fallbackText(assessment);
            log.info("Empfehlung {}: {} (Form {}, Ramp {}, capped={})", requestId, assessment.category(),
                    assessment.form(), assessment.rampRate(), assessment.rampCapped());

            if (assessment.category() == RecommendationCategory.INSUFFICIENT_DATA) {
                RecommendationUpdated only = message(requestId, assessment, fallback, now, false, false);
                synchronized (stateLock) {
                    pending = null;
                }
                publish(only);
                return only;
            }

            RecommendationUpdated first = message(requestId, assessment, fallback, now, true, true);
            synchronized (stateLock) {
                pending = first;
                pendingSince = clock.instant();
            }
            publish(first);
            try {
                publisher.publish(Topics.AI_RECOMMENDATION_REQUEST, new RecommendationTextRequest(requestId,
                        assessment.category().getLabel(), assessment.form(),
                        assessment.rampRate() != null ? assessment.rampRate() : 0.0, assessment.rampCapped(),
                        last7DaysSummary(today)));
            } catch (RuntimeException e) {
                log.warn("KI-Text nicht angefragt, Fallback bleibt: {}", e.getMessage());
                finish(requestId, null);
            }
            return first;
        }
    }

    /** Antwort des ai-service. Antworten auf aeltere Berechnungen werden ignoriert. */
    public void applyText(RecommendationTextResult result) {
        if (result.success() && result.text() != null && !result.text().isBlank()) {
            finish(result.requestId(), result.text());
        } else {
            log.warn("KI-Text fuer {} nicht verfuegbar, Fallback bleibt: {}", result.requestId(), result.error());
            finish(result.requestId(), null);
        }
    }

    /** Beendet einen offenen KI-Auftrag mit dem Fallback, wenn er laenger als {@code textTimeout} offen ist. */
    public void expirePending() {
        String expired = null;
        synchronized (stateLock) {
            if (pending != null && !clock.instant().isBefore(pendingSince.plus(textTimeout))) {
                expired = pending.requestId();
            }
        }
        if (expired != null) {
            log.warn("Kein KI-Text fuer {} nach {} - Fallback bleibt", expired, textTimeout);
            finish(expired, null);
        }
    }

    /** @param text KI-Text; null = Fallback behalten */
    private void finish(String requestId, String text) {
        RecommendationUpdated done;
        synchronized (stateLock) {
            if (pending == null || !pending.requestId().equals(requestId)) {
                log.debug("Empfehlungstext {} ignoriert (nicht mehr offen)", requestId);
                return;
            }
            RecommendationUpdated p = pending;
            done = new RecommendationUpdated(p.requestId(), p.category(), p.categoryLabel(), p.color(),
                    text != null ? text : p.text(), p.form(), p.rampRate(), p.rampCapped(), p.refDate(),
                    p.computedAt(), false, text == null);
            pending = null;
        }
        publish(done);
    }

    private String last7DaysSummary(LocalDate today) {
        try {
            List<IntervalsActivity> rides = intervals.activities(
                    today.minusDays(RecommendationCalculator.SUMMARY_DAYS), today);
            return RecommendationCalculator.last7DaysSummary(rides);
        } catch (RuntimeException e) {
            log.warn("Fahrten der letzten Tage nicht ladbar, KI-Text ohne Fahrten: {}", e.getMessage());
            return "";
        }
    }

    private void publish(RecommendationUpdated update) {
        try {
            publisher.publishRetained(Topics.RECOMMENDATION_UPDATED, update);
        } catch (RuntimeException e) {
            log.warn("Empfehlung {} nicht veroeffentlicht: {}", update.requestId(), e.getMessage());
        }
        for (Consumer<RecommendationUpdated> listener : listeners) {
            try {
                listener.accept(update);
            } catch (RuntimeException e) {
                log.error("Listener fuer Empfehlung {} fehlgeschlagen: {}", update.requestId(), e.getMessage(), e);
            }
        }
    }

    private static RecommendationUpdated message(String requestId, Assessment a, String text, LocalDateTime now,
                                                 boolean textPending, boolean fallbackUsed) {
        return new RecommendationUpdated(requestId, a.category().name(), a.category().getLabel(),
                a.category().getColor(), text, a.form(), a.rampRate(), a.rampCapped(), a.refDate(), now,
                textPending, fallbackUsed);
    }
}
