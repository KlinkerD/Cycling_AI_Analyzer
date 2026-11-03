package com.cycling.service;

import com.cycling.dto.DailyRecommendationDto;
import com.cycling.messaging.MessagePublisher;
import com.cycling.messaging.Topics;
import com.cycling.messaging.contract.RecommendationRefreshRequest;
import com.cycling.messaging.contract.RecommendationUpdated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Haelt die Tagesempfehlung des recommendation-service fuer die Overview-Karte. Gerechnet wird hier nichts:
 * der Stand kommt retained per MQTT ({@link Topics#RECOMMENDATION_UPDATED}) und liegt nur im Speicher; nach
 * einem Neustart liefert der Broker ihn sofort wieder.
 *
 * <p>Refresh: Auftrag mit requestId auf {@link Topics#RECOMMENDATION_REFRESH}, dann bis zu
 * {@code refreshTimeout} auf die erste Antwort mit dieser ID warten. Ohne Antwort gilt der letzte Stand;
 * gibt es noch gar keinen, wirft er {@link RecommendationUnavailableException} (503).
 */
public class RecommendationStore {

    private static final Logger log = LoggerFactory.getLogger(RecommendationStore.class);

    private final MessagePublisher publisher;
    private final Clock clock;
    private final Duration refreshTimeout;
    private final Map<String, CompletableFuture<RecommendationUpdated>> waiting = new ConcurrentHashMap<>();

    /** Letzter Stand plus Zeitpunkt, zu dem seine erste Nachricht hier ankam (fuer das textPending-Limit). */
    private record Received(RecommendationUpdated update, Instant firstSeen) {
    }

    private volatile Received latest;

    public RecommendationStore(MessagePublisher publisher, Clock clock, Duration refreshTimeout) {
        this.publisher = publisher;
        this.clock = clock;
        this.refreshTimeout = refreshTimeout;
    }

    /** Neuer Stand vom recommendation-service (MQTT-Handler). */
    public void onUpdate(RecommendationUpdated update) {
        if (update == null || update.requestId() == null || update.category() == null) {
            log.warn("Unvollstaendige Empfehlung ignoriert: {}", update);
            return;
        }
        synchronized (this) {
            Received previous = latest;
            Instant firstSeen = previous != null && previous.update().requestId().equals(update.requestId())
                    ? previous.firstSeen() : clock.instant();
            latest = new Received(update, firstSeen);
        }
        CompletableFuture<RecommendationUpdated> waiter = waiting.remove(update.requestId());
        if (waiter != null) waiter.complete(update);
    }

    /** Aktueller Stand; beim Kaltstart (noch nie etwas empfangen) wird einmal neu angefragt. */
    public DailyRecommendationDto current() {
        Received r = latest;
        return r != null ? toDto(r) : refresh();
    }

    /** Neu berechnen lassen und kurz auf das Ergebnis warten. */
    public DailyRecommendationDto refresh() {
        String requestId = UUID.randomUUID().toString();
        CompletableFuture<RecommendationUpdated> answer = new CompletableFuture<>();
        waiting.put(requestId, answer);
        try {
            publisher.publish(Topics.RECOMMENDATION_REFRESH, new RecommendationRefreshRequest(requestId));
            answer.get(refreshTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("Keine Antwort des recommendation-service auf {} nach {} - letzter Stand bleibt", requestId, refreshTimeout);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | RuntimeException e) {
            log.warn("Refresh der Empfehlung nicht angefragt: {}", e.getMessage());
        } finally {
            waiting.remove(requestId);
        }
        Received r = latest;
        if (r == null) {
            throw new RecommendationUnavailableException(
                    "Die Tagesempfehlung ist noch nicht verfügbar – der recommendation-service antwortet nicht.");
        }
        return toDto(r);
    }

    private DailyRecommendationDto toDto(Received r) {
        // Haengt der recommendation-service, soll das Frontend nicht endlos auf den KI-Text warten.
        boolean textPending = r.update().textPending()
                && clock.instant().isBefore(r.firstSeen().plus(DailyRecommendationDto.TEXT_WAIT));
        return DailyRecommendationDto.from(r.update(), textPending);
    }
}
