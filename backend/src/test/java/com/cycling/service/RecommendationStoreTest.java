package com.cycling.service;

import com.cycling.dto.DailyRecommendationDto;
import com.cycling.messaging.MessagePublisher;
import com.cycling.messaging.MessagingException;
import com.cycling.messaging.Topics;
import com.cycling.messaging.contract.RecommendationRefreshRequest;
import com.cycling.messaging.contract.RecommendationUpdated;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Das backend zeigt nur an: Stand per MQTT, Refresh als Auftrag mit kurzem Warten auf die passende Antwort. */
class RecommendationStoreTest {

    private static final Instant T0 = Instant.parse("2026-11-03T08:00:00Z");

    private final MovableClock clock = new MovableClock();
    private final AnsweringPublisher publisher = new AnsweringPublisher();
    private final RecommendationStore store = new RecommendationStore(publisher, clock, Duration.ofMillis(300));

    /** Ruft beim Refresh-Auftrag die eingestellte Antwort auf (wie der recommendation-service). */
    static class AnsweringPublisher implements MessagePublisher {
        Consumer<String> onRefresh = id -> { };
        RuntimeException failure;
        int refreshes;

        @Override
        public void publish(String topic, Object message) {
            if (failure != null) throw failure;
            if (topic.equals(Topics.RECOMMENDATION_REFRESH)) {
                refreshes++;
                onRefresh.accept(((RecommendationRefreshRequest) message).requestId());
            }
        }

        @Override
        public void publishRetained(String topic, Object message) {
            throw new UnsupportedOperationException();
        }
    }

    static class MovableClock extends Clock {
        Instant now = T0;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static RecommendationUpdated update(String requestId, String text, boolean textPending) {
        return new RecommendationUpdated(requestId, "TEMPO", "Tempo / Sweet Spot", "#ff9800", text, 8.0, 3.0, false,
                LocalDate.of(2026, 11, 3), LocalDateTime.of(2026, 11, 3, 9, 0), textPending, textPending);
    }

    @Test
    void showsRetainedStateWithoutAsking() {
        store.onUpdate(update("r1", "Heute Sweet Spot!", false));

        DailyRecommendationDto dto = store.current();

        assertThat(dto.text()).isEqualTo("Heute Sweet Spot!");
        assertThat(dto.category()).isEqualTo("TEMPO");
        assertThat(dto.categoryLabel()).isEqualTo("Tempo / Sweet Spot");
        assertThat(dto.textPending()).isFalse();
        assertThat(publisher.refreshes).isZero();
    }

    @Test
    void refreshReturnsAnswerWithMatchingRequestId() {
        store.onUpdate(update("alt", "Alter Stand", false));
        publisher.onRefresh = id -> new Thread(() -> {
            store.onUpdate(update("fremd", "Zeitplan", true)); // andere Berechnung beendet das Warten nicht
            store.onUpdate(update(id, "Neu", true));
        }).start();

        DailyRecommendationDto dto = store.refresh();

        assertThat(dto.text()).isEqualTo("Neu");
        assertThat(dto.textPending()).isTrue();
    }

    @Test
    void refreshWithoutAnswerKeepsLastState() {
        store.onUpdate(update("alt", "Alter Stand", false));

        assertThat(store.refresh().text()).isEqualTo("Alter Stand");
    }

    @Test
    void brokerOutageKeepsLastState() {
        store.onUpdate(update("alt", "Alter Stand", false));
        publisher.failure = new MessagingException("nicht verbunden", null);

        assertThat(store.refresh().text()).isEqualTo("Alter Stand");
    }

    @Test
    void coldStartAsksOnce() {
        publisher.onRefresh = id -> store.onUpdate(update(id, "Erste Empfehlung", true));

        assertThat(store.current().text()).isEqualTo("Erste Empfehlung");
        assertThat(publisher.refreshes).isEqualTo(1);
    }

    @Test
    void coldStartWithoutAnswerIsUnavailable() {
        assertThatThrownBy(store::current).isInstanceOf(RecommendationUnavailableException.class);
    }

    @Test
    void secondMessageReplacesFirst() {
        store.onUpdate(update("r1", "Fallback", true));
        store.onUpdate(update("r1", "KI-Text", false));

        DailyRecommendationDto dto = store.current();
        assertThat(dto.text()).isEqualTo("KI-Text");
        assertThat(dto.textPending()).isFalse();
    }

    @Test
    void textPendingEndsAfterTwoMinutesFromFirstMessage() {
        store.onUpdate(update("r1", "Fallback", true));
        clock.now = T0.plus(Duration.ofSeconds(90));
        store.onUpdate(update("r1", "Fallback", true)); // Wiederholung zaehlt nicht neu
        assertThat(store.current().textPending()).isTrue();

        clock.now = T0.plus(DailyRecommendationDto.TEXT_WAIT);

        assertThat(store.current().textPending()).isFalse();
    }

    @Test
    void incompleteMessagesAreIgnored() {
        store.onUpdate(new RecommendationUpdated(null, null, null, null, null, null, null, false, null, null, false, false));

        assertThatThrownBy(store::current).isInstanceOf(RecommendationUnavailableException.class);
    }
}
