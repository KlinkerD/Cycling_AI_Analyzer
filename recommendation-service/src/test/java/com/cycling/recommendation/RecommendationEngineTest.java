package com.cycling.recommendation;

import com.cycling.intervals.client.IntervalsUnavailableException;
import com.cycling.intervals.api.IntervalsActivity;
import com.cycling.intervals.api.IntervalsWellnessPoint;
import com.cycling.messaging.Topics;
import com.cycling.messaging.contract.RecommendationTextRequest;
import com.cycling.messaging.contract.RecommendationTextResult;
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
            return List.of(new IntervalsActivity("i1", "Runde", "2026-11-02T08:00:00", "Ride", 40000.0, null, null,
                    null, null, null, null, null, null, null, 70.0, 0.75));
        }
    }

    private List<Recommendation> updates() {
        return seen;
    }

    @Test
    void firstMessageIsFallbackAndRequestsText() {
        Recommendation first = engine.compute("r1");

        assertThat(first.textPending()).isTrue();
        assertThat(first.fallbackUsed()).isTrue();
        assertThat(first.category()).isEqualTo("TEMPO");
        assertThat(first.color()).isEqualTo("#ff9800");
        assertThat(first.computedAt()).isEqualTo(LocalDateTime.of(2026, 11, 3, 9, 0));
        assertThat(engine.latest()).contains(first);
        RecommendationTextRequest request = publisher.on(Topics.AI_RECOMMENDATION_REQUEST, RecommendationTextRequest.class).getFirst();
        assertThat(request.requestId()).isEqualTo("r1");
        assertThat(request.last7DaysSummary()).contains("Runde, 70 TSS, IF 0,75, 40 km");
    }

    @Test
    void textArrivesAsSecondMessage() {
        engine.compute("r1");

        engine.applyText(new RecommendationTextResult("r1", true, null, "Heute Sweet Spot!"));

        assertThat(updates()).hasSize(2);
        Recommendation second = updates().get(1);
        assertThat(second.requestId()).isEqualTo("r1");
        assertThat(second.text()).isEqualTo("Heute Sweet Spot!");
        assertThat(second.textPending()).isFalse();
        assertThat(second.fallbackUsed()).isFalse();
        assertThat(engine.latest()).contains(second);
    }

    @Test
    void failedTextStillSendsSecondMessageWithFallback() {
        Recommendation first = engine.compute("r1");

        engine.applyText(RecommendationTextResult.failed("r1", "HTTP 500"));

        Recommendation second = updates().get(1);
        assertThat(second.text()).isEqualTo(first.text());
        assertThat(second.textPending()).isFalse();
        assertThat(second.fallbackUsed()).isTrue();
    }

    @Test
    void textForOlderComputationIsIgnored() {
        engine.compute("r1");
        engine.compute("r2");

        engine.applyText(new RecommendationTextResult("r1", true, null, "Veraltet"));

        assertThat(updates()).extracting(Recommendation::requestId).containsExactly("r1", "r2");
    }

    @Test
    void missingTextExpiresAfterTimeout() {
        engine.compute("r1");
        clock.advance(Duration.ofSeconds(89));
        engine.expirePending();
        assertThat(updates()).hasSize(1);

        clock.advance(Duration.ofSeconds(1));
        engine.expirePending();

        assertThat(updates()).hasSize(2);
        assertThat(updates().get(1).textPending()).isFalse();
        engine.applyText(new RecommendationTextResult("r1", true, null, "zu spaet"));
        assertThat(updates()).hasSize(2);
    }

    @Test
    void brokerOutageForAiRequestFinishesImmediately() {
        publisher.failingTopic = Topics.AI_RECOMMENDATION_REQUEST;

        engine.compute("r1");

        assertThat(updates()).extracting(Recommendation::textPending).containsExactly(true, false);
    }

    @Test
    void insufficientDataIsSingleFinalMessage() {
        intervals.wellness = List.of();

        engine.compute("r1");

        assertThat(updates()).hasSize(1);
        assertThat(updates().getFirst().category()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(updates().getFirst().textPending()).isFalse();
        assertThat(publisher.on(Topics.AI_RECOMMENDATION_REQUEST, Object.class)).isEmpty();
    }

    @Test
    void intervalsOutageSetsNothing() {
        intervals.wellnessError = new IntervalsUnavailableException("weg", null);

        assertThatThrownBy(() -> engine.compute("r1")).isInstanceOf(IntervalsUnavailableException.class);
        engine.computeScheduled();

        assertThat(seen).isEmpty();
        assertThat(engine.latest()).isEmpty();
        assertThat(publisher.sent).isEmpty();
    }

    @Test
    void missingActivitiesOnlyShortenThePrompt() {
        intervals.activitiesError = new IntervalsUnavailableException("weg", null);

        engine.compute("r1");

        assertThat(publisher.on(Topics.AI_RECOMMENDATION_REQUEST, RecommendationTextRequest.class).getFirst()
                .last7DaysSummary()).isEmpty();
    }
}
