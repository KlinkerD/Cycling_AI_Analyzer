package com.cycling.training.recommendation;

import com.cycling.intervals.client.IntervalsUnavailableException;
import com.cycling.intervals.api.IntervalsActivity;
import com.cycling.messaging.MessagePublisher;
import com.cycling.messaging.Topics;
import com.cycling.messaging.contract.AiRequest;
import com.cycling.messaging.contract.AiResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Berechnet die Tagesempfehlung und haelt den letzten Stand fuer die REST-API ({@link #latest()}).
 *
 * <p>Pro Berechnung zwei Staende mit derselben requestId: sofort mit Fallback-Text
 * ({@code textPending=true}), dann mit KI-Text. Kommt kein KI-Text (Fehler, ai-service weg,
 * {@code textTimeout} abgelaufen), wird der zweite Stand mit dem Fallback gesetzt. Ohne Daten gibt es
 * nur einen Stand ({@code INSUFFICIENT_DATA}, nichts mehr erwartet). Listener bekommen jeden Stand.
 *
 * <p>Ist der intervals-service nicht erreichbar, bleibt der letzte Stand.
 *
 * <p>Locks: Berechnungen laufen nacheinander ({@code computeLock}); der Zustand des offenen KI-Auftrags
 * liegt unter {@code stateLock}. Listener werden nie unter {@code stateLock} aufgerufen.
 */
public class RecommendationEngine {

    private static final Logger log = LoggerFactory.getLogger(RecommendationEngine.class);
    /** Quelle beim ai-service: Auftraege auf cycling/ai/request/training, Antworten auf cycling/ai/result/training. */
    public static final String AI_SOURCE = "training";

    private final IntervalsData intervals;
    private final MessagePublisher publisher;
    private final Clock clock;
    private final Duration textTimeout;
    private final List<Consumer<Recommendation>> listeners = new CopyOnWriteArrayList<>();

    private final Object computeLock = new Object();
    private final Object stateLock = new Object();
    /** Zuletzt gesetzter Stand; null, solange seit dem Start noch nichts berechnet wurde. */
    private volatile Recommendation latest;
    /** Erster Stand der Berechnung, deren KI-Text noch aussteht; null = nichts offen. */
    private Recommendation pending;
    private Instant pendingSince;

    /** @param textTimeout so lange wird hoechstens auf den KI-Text gewartet */
    public RecommendationEngine(IntervalsData intervals, MessagePublisher publisher, Clock clock, Duration textTimeout) {
        this.intervals = intervals;
        this.publisher = publisher;
        this.clock = clock;
        this.textTimeout = textTimeout;
    }

    /** Bekommt jeden neuen Stand (beide pro Berechnung), z. B. fuer den Tagesversand. */
    public void addListener(Consumer<Recommendation> listener) {
        listeners.add(listener);
    }

    /** Letzter Stand; leer, solange seit dem Start noch nichts berechnet wurde. */
    public Optional<Recommendation> latest() {
        return Optional.ofNullable(latest);
    }

    /** Zeitplan (alle paar Stunden). */
    public void computeScheduled() {
        computeQuietly(UUID.randomUUID(), "Zeitplan");
    }

    private void computeQuietly(UUID requestId, String trigger) {
        try {
            compute(requestId);
        } catch (RuntimeException e) {
            log.warn("Tagesempfehlung ({}) nicht berechnet, letzter Stand bleibt: {}", trigger, e.getMessage());
        }
    }

    /**
     * Berechnet neu und setzt den ersten Stand.
     *
     * @return der erste Stand dieser Berechnung
     * @throws IntervalsUnavailableException wenn keine Wellness-Daten geladen werden konnten
     */
    public Recommendation compute(UUID requestId) {
        synchronized (computeLock) {
            LocalDate today = LocalDate.now(clock);
            Assessment assessment = RecommendationCalculator.assess(
                    intervals.wellness(today.minusDays(RecommendationCalculator.WELLNESS_DAYS), today));
            LocalDateTime now = LocalDateTime.now(clock);
            String fallback = RecommendationCalculator.fallbackText(assessment);
            log.info("Empfehlung {}: {} (Form {}, Ramp {}, capped={})", requestId, assessment.category(),
                    assessment.form(), assessment.rampRate(), assessment.rampCapped());

            if (assessment.category() == RecommendationCategory.INSUFFICIENT_DATA) {
                Recommendation only = message(requestId, assessment, fallback, now, false, false);
                synchronized (stateLock) {
                    pending = null;
                }
                publish(only);
                return only;
            }

            Recommendation first = message(requestId, assessment, fallback, now, true, true);
            synchronized (stateLock) {
                pending = first;
                pendingSince = clock.instant();
            }
            publish(first);
            try {
                publisher.publish(Topics.aiRequest(AI_SOURCE), new AiRequest(requestId,
                        RecommendationPrompt.build(assessment, last7DaysSummary(today)),
                        RecommendationPrompt.TEMPERATURE, false));
            } catch (RuntimeException e) {
                log.warn("KI-Text nicht angefragt, Fallback bleibt: {}", e.getMessage());
                finish(requestId, null);
            }
            return first;
        }
    }

    /** Antwort des ai-service. Antworten auf aeltere Berechnungen werden ignoriert. */
    public void applyText(AiResult result) {
        if (result.success() && result.text() != null && !result.text().isBlank()) {
            finish(result.requestId(), result.text());
        } else {
            log.warn("KI-Text fuer {} nicht verfuegbar, Fallback bleibt: {}", result.requestId(), result.error());
            finish(result.requestId(), null);
        }
    }

    /** Beendet einen offenen KI-Auftrag mit dem Fallback, wenn er laenger als {@code textTimeout} offen ist. */
    public void expirePending() {
        UUID expired = null;
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
    private void finish(UUID requestId, String text) {
        Recommendation done;
        synchronized (stateLock) {
            if (pending == null || !pending.requestId().equals(requestId)) {
                log.debug("Empfehlungstext {} ignoriert (nicht mehr offen)", requestId);
                return;
            }
            Recommendation p = pending;
            done = new Recommendation(p.requestId(), p.category(), p.categoryLabel(), p.color(),
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

    private void publish(Recommendation update) {
        latest = update;
        for (Consumer<Recommendation> listener : listeners) {
            try {
                listener.accept(update);
            } catch (RuntimeException e) {
                log.error("Listener fuer Empfehlung {} fehlgeschlagen: {}", update.requestId(), e.getMessage(), e);
            }
        }
    }

    private static Recommendation message(UUID requestId, Assessment a, String text, LocalDateTime now,
                                                 boolean textPending, boolean fallbackUsed) {
        return new Recommendation(requestId, a.category().name(), a.category().getLabel(),
                a.category().getColor(), text, a.form(), a.rampRate(), a.rampCapped(), a.refDate(), now,
                textPending, fallbackUsed);
    }
}
