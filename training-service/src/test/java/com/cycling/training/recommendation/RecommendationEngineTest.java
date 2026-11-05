package com.cycling.training.recommendation;

import java.util.UUID;
import com.cycling.types.ActivityId;
import com.cycling.intervals.client.IntervalsUnavailableException;
import com.cycling.intervals.api.IntervalsActivity;
import com.cycling.intervals.api.IntervalsWellnessPoint;
import com.cycling.messaging.Topics;
import com.cycling.messaging.contract.AiRequest;
import com.cycling.messaging.contract.AiResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Zwei Staende pro Berechnung: sofort Fallback, dann KI-Text oder - bei Ausfall - noch einmal Fallback. */
class RecommendationEngineTest {

    /** Feste UUID pro Name, damit Tests lesbar bleiben. */
    private static UUID uuid(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private final MutableClock clock = new MutableClock(LocalDateTime.of(2026, 11, 3, 9, 0));
    private final RecordingPublisher publisher = new RecordingPublisher();
    private final FakeIntervals intervals = new FakeIntervals();
    private final RecommendationEngine engine = new RecommendationEngine(intervals, publisher, clock, Duration.ofSeconds(90));
    private final List<Recommendation> seen = new ArrayList<>();

    {
        engine.addListener(seen::add);
    }

    static class FakeIntervals implements IntervalsData {
        List<IntervalsWellnessPoint> wellness = List.of(new IntervalsWellnessPoint("2026-11-03", 60.0, 50.0, 10.0, 2.0));
        RuntimeException wellnessError;
        RuntimeException activitiesError;

        @Override
        public List<IntervalsWellnessPoint> wellness(LocalDate from, LocalDate to) {
            if (wellnessError != null) throw wellnessError;
            return wellness;
        }

        @Override
        public List<IntervalsActivity> activities(LocalDate from, LocalDate to) {
            if (activitiesError != null) throw activitiesError;
            return List.of(new IntervalsActivity(ActivityId.of("i1"), "Runde", LocalDateTime.parse("2026-11-02T08:00:00"), "Ride", 40000.0, null, null,
                    null, null, null, null, null, null, null, 70.0, 0.75, null));
        }
    }

    private List<Recommendation> updates() {
        return seen;
    }

    @Test
    void firstMessageIsFallbackAndRequestsText() {
        Recommendation first = engine.compute(uuid("r1"));

        assertThat(first.textPending()).isTrue();
        assertThat(first.fallbackUsed()).isTrue();
        assertThat(first.category()).isEqualTo("HARD_INTERVALS"); // Form +10 = Zone "Frisch"
        assertThat(first.color()).isEqualTo("#ef5350");
        assertThat(first.computedAt()).isEqualTo(LocalDateTime.of(2026, 11, 3, 9, 0));
        assertThat(engine.latest()).contains(first);
        AiRequest request = publisher.on(Topics.aiRequest(RecommendationEngine.AI_SOURCE), AiRequest.class).getFirst();
        assertThat(request.requestId()).isEqualTo(uuid("r1"));
        assertThat(request.prompt()).contains("Runde, 70 TSS, IF 0,75, 40 km");
        assertThat(request.prompt()).contains("darf NICHT geaendert werden: Harte Intervalle");
        assertThat(request.json()).isFalse();
        assertThat(request.temperature()).isEqualTo(RecommendationPrompt.TEMPERATURE);
    }

    @Test
    void textArrivesAsSecondMessage() {
        engine.compute(uuid("r1"));

        engine.applyText(new AiResult(uuid("r1"), true, null, "Heute Sweet Spot!"));

        assertThat(updates()).hasSize(2);
        Recommendation second = updates().get(1);
        assertThat(second.requestId()).isEqualTo(uuid("r1"));
        assertThat(second.text()).isEqualTo("Heute Sweet Spot!");
        assertThat(second.textPending()).isFalse();
        assertThat(second.fallbackUsed()).isFalse();
        assertThat(engine.latest()).contains(second);
    }

    @Test
    void failedTextStillSendsSecondMessageWithFallback() {
        Recommendation first = engine.compute(uuid("r1"));

        engine.applyText(AiResult.failed(uuid("r1"), "HTTP 500"));

        Recommendation second = updates().get(1);
        assertThat(second.text()).isEqualTo(first.text());
        assertThat(second.textPending()).isFalse();
        assertThat(second.fallbackUsed()).isTrue();
    }

    @Test
    void textForOlderComputationIsIgnored() {
        engine.compute(uuid("r1"));
        engine.compute(uuid("r2"));

        engine.applyText(new AiResult(uuid("r1"), true, null, "Veraltet"));

        assertThat(updates()).extracting(Recommendation::requestId).containsExactly(uuid("r1"), uuid("r2"));
    }

    @Test
    void missingTextExpiresAfterTimeout() {
        engine.compute(uuid("r1"));
        clock.advance(Duration.ofSeconds(89));
        engine.expirePending();
        assertThat(updates()).hasSize(1);

        clock.advance(Duration.ofSeconds(1));
        engine.expirePending();

        assertThat(updates()).hasSize(2);
        assertThat(updates().get(1).textPending()).isFalse();
        engine.applyText(new AiResult(uuid("r1"), true, null, "zu spaet"));
        assertThat(updates()).hasSize(2);
    }

    @Test
    void brokerOutageForAiRequestFinishesImmediately() {
        publisher.failingTopic = Topics.aiRequest(RecommendationEngine.AI_SOURCE);

        engine.compute(uuid("r1"));

        assertThat(updates()).extracting(Recommendation::textPending).containsExactly(true, false);
    }

    @Test
    void insufficientDataIsSingleFinalMessage() {
        intervals.wellness = List.of();

        engine.compute(uuid("r1"));

        assertThat(updates()).hasSize(1);
        assertThat(updates().getFirst().category()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(updates().getFirst().textPending()).isFalse();
        assertThat(publisher.on(Topics.aiRequest(RecommendationEngine.AI_SOURCE), Object.class)).isEmpty();
    }

    @Test
    void intervalsOutageSetsNothing() {
        intervals.wellnessError = new IntervalsUnavailableException("weg", null);

        assertThatThrownBy(() -> engine.compute(uuid("r1"))).isInstanceOf(IntervalsUnavailableException.class);
        engine.computeScheduled();

        assertThat(seen).isEmpty();
        assertThat(engine.latest()).isEmpty();
        assertThat(publisher.sent).isEmpty();
    }

    @Test
    void missingActivitiesOnlyShortenThePrompt() {
        intervals.activitiesError = new IntervalsUnavailableException("weg", null);

        engine.compute(uuid("r1"));

        assertThat(publisher.on(Topics.aiRequest(RecommendationEngine.AI_SOURCE), AiRequest.class).getFirst()
                .prompt()).contains("- Keine Aktivitaeten erfasst.");
    }
}
