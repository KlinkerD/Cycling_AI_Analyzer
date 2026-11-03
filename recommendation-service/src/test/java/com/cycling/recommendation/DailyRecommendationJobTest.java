package com.cycling.recommendation;

import com.cycling.messaging.contract.DeliveryMessage;
import com.cycling.messaging.contract.DeliveryMessage.Target;
import com.cycling.messaging.contract.RecommendationTextResult;
import com.cycling.messaging.contract.RecommendationUpdated;
import com.cycling.messaging.Topics;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Versand um 09:00 mit KI-Text, spaetestens nach 2 Minuten mit Fallback, nachgeholt bis 12:00, nie doppelt. */
class DailyRecommendationJobTest {

    private final MutableClock clock = new MutableClock(LocalDateTime.of(2026, 11, 3, 8, 59));
    private final RecordingPublisher publisher = new RecordingPublisher();
    private final RecommendationEngineTest.FakeIntervals intervals = new RecommendationEngineTest.FakeIntervals();
    private final RecommendationEngine engine = new RecommendationEngine(intervals, publisher, clock, Duration.ofSeconds(90));
    private final DailyRecommendationJob job = new DailyRecommendationJob(engine,
            new RecommendationMessageBuilder(List.of(new Target("DISCORD", "recommendation"))), publisher, clock,
            LocalTime.of(9, 0), LocalTime.of(12, 0), Duration.ofMinutes(2));

    {
        engine.addListener(job);
    }

    private List<DeliveryMessage> delivered() {
        return publisher.on(DailyRecommendationJob.TOPIC, DeliveryMessage.class);
    }

    private String pendingRequestId() {
        return publisher.on(Topics.RECOMMENDATION_UPDATED, RecommendationUpdated.class).getLast().requestId();
    }

    @Test
    void nothingBeforeSendTime() {
        job.check();

        assertThat(publisher.sent).isEmpty();
    }

    @Test
    void computesAtNineAndSendsOnceAiTextArrives() {
        clock.set(LocalDateTime.of(2026, 11, 3, 9, 0));
        job.check();
        assertThat(delivered()).isEmpty();

        engine.applyText(new RecommendationTextResult(pendingRequestId(), true, null, "Heute Sweet Spot!"));

        assertThat(delivered()).hasSize(1);
        DeliveryMessage message = delivered().getFirst();
        assertThat(message.messageId()).isEqualTo("recommendation-daily-2026-11-03");
        assertThat(message.targets()).containsExactly(new Target("DISCORD", "recommendation"));
        assertThat(message.sections().getFirst().text()).isEqualTo("Heute Sweet Spot!");
        assertThat(message.footer()).doesNotContain("KI-Text nicht");
    }

    @Test
    void sendsFallbackAfterTwoMinutesWithoutText() {
        clock.set(LocalDateTime.of(2026, 11, 3, 9, 0));
        job.check();

        clock.advance(Duration.ofSeconds(119));
        job.check();
        assertThat(delivered()).isEmpty();

        clock.advance(Duration.ofSeconds(1));
        job.check();

        assertThat(delivered()).hasSize(1);
        assertThat(delivered().getFirst().footer()).contains("KI-Text nicht verfügbar");
    }

    @Test
    void neverSendsTwiceOnTheSameDay() {
        clock.set(LocalDateTime.of(2026, 11, 3, 9, 0));
        job.check();
        engine.applyText(new RecommendationTextResult(pendingRequestId(), true, null, "Text"));

        clock.set(LocalDateTime.of(2026, 11, 3, 10, 0));
        job.check();
        engine.computeScheduled();
        engine.applyText(new RecommendationTextResult(pendingRequestId(), true, null, "Anderer Text"));
        job.check();

        assertThat(delivered()).hasSize(1);
    }

    @Test
    void refreshInBetweenDoesNotBlockTheDailySend() {
        clock.set(LocalDateTime.of(2026, 11, 3, 9, 0));
        job.check();
        engine.compute("refresh-vom-backend"); // ersetzt den offenen KI-Auftrag des Jobs

        clock.advance(Duration.ofMinutes(2));
        job.check();

        assertThat(delivered()).hasSize(1);
    }

    @Test
    void catchesUpAfterRestartBeforeNoon() {
        clock.set(LocalDateTime.of(2026, 11, 3, 11, 59));
        job.check();
        engine.applyText(new RecommendationTextResult(pendingRequestId(), true, null, "Text"));

        assertThat(delivered()).hasSize(1);
    }

    @Test
    void noCatchUpFromNoon() {
        clock.set(LocalDateTime.of(2026, 11, 3, 12, 0));
        job.check();

        assertThat(publisher.sent).isEmpty();
    }

    @Test
    void retriesNextMinuteWhenIntervalsIsDown() {
        clock.set(LocalDateTime.of(2026, 11, 3, 9, 0));
        intervals.wellnessError = new IntervalsUnavailableException("weg", null);
        job.check();
        assertThat(publisher.sent).isEmpty();

        intervals.wellnessError = null;
        clock.advance(Duration.ofMinutes(1));
        job.check();
        engine.applyText(new RecommendationTextResult(pendingRequestId(), true, null, "Text"));

        assertThat(delivered()).hasSize(1);
    }

    @Test
    void retriesNextMinuteWhenHandOverFails() {
        clock.set(LocalDateTime.of(2026, 11, 3, 9, 0));
        publisher.failingTopic = DailyRecommendationJob.TOPIC;
        job.check();
        engine.applyText(new RecommendationTextResult(pendingRequestId(), true, null, "Text"));
        assertThat(delivered()).isEmpty();

        publisher.failingTopic = null;
        clock.advance(Duration.ofMinutes(1));
        job.check();

        assertThat(delivered()).hasSize(1);
        assertThat(delivered().getFirst().sections().getFirst().text()).isEqualTo("Text");
    }

    @Test
    void insufficientDataIsSentRightAway() {
        clock.set(LocalDateTime.of(2026, 11, 3, 9, 0));
        intervals.wellness = List.of();

        job.check();

        assertThat(delivered()).hasSize(1);
        assertThat(delivered().getFirst().sections().getFirst().title()).isEqualTo("Nicht genug Daten");
    }

    @Test
    void sendsAgainNextDay() {
        clock.set(LocalDateTime.of(2026, 11, 3, 9, 0));
        job.check();
        engine.applyText(new RecommendationTextResult(pendingRequestId(), true, null, "Text"));

        clock.set(LocalDateTime.of(2026, 11, 4, 9, 0));
        job.check();
        engine.applyText(new RecommendationTextResult(pendingRequestId(), true, null, "Text"));

        assertThat(delivered()).extracting(DeliveryMessage::messageId)
                .containsExactly("recommendation-daily-2026-11-03", "recommendation-daily-2026-11-04");
    }

    @Test
    void catchUpMustBeAfterSendTime() {
        assertThatThrownBy(() -> new DailyRecommendationJob(engine, new RecommendationMessageBuilder(List.of()),
                publisher, clock, LocalTime.of(9, 0), LocalTime.of(9, 0), Duration.ofMinutes(2)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
