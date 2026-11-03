package com.cycling.fitness;

import com.cycling.messaging.MessagePublisher;
import com.cycling.messaging.MessagingException;
import com.cycling.messaging.contract.DeliveryMessage;
import com.cycling.messaging.contract.WellnessUpdated;
import com.cycling.messaging.contract.WellnessUpdated.Values;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FitnessSummaryJobTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    /** Uhr, die der Test vorstellen kann. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(LocalDateTime start) {
            set(start);
        }

        void set(LocalDateTime local) {
            now = local.atZone(BERLIN).toInstant();
        }

        @Override
        public ZoneId getZone() {
            return BERLIN;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    /** Merkt sich Publishes; kann auf "Broker weg" geschaltet werden. */
    private static final class RecordingPublisher implements MessagePublisher {
        final List<String> topics = new ArrayList<>();
        final List<DeliveryMessage> messages = new ArrayList<>();
        boolean failing;

        @Override
        public void publish(String topic, Object message) {
            if (failing) throw new MessagingException("Broker weg", null);
            topics.add(topic);
            messages.add((DeliveryMessage) message);
        }

        @Override
        public void publishRetained(String topic, Object message) {
            throw new UnsupportedOperationException();
        }
    }

    private final MutableClock clock = new MutableClock(LocalDateTime.of(2026, 11, 3, 12, 0));
    private final RecordingPublisher publisher = new RecordingPublisher();
    private final FitnessSummaryJob job = new FitnessSummaryJob(
            new FitnessMessageBuilder(List.of()), publisher, clock, LocalTime.of(21, 0));

    private static WellnessUpdated stand(String date) {
        return new WellnessUpdated(new Values(date, 50.0, 55.0, -5.0, 3.0), null);
    }

    private void at(int day, int hour, int minute) {
        clock.set(LocalDateTime.of(2026, 11, day, hour, minute));
    }

    @Test
    void sendsNothingBeforeTheSummaryTime() {
        job.onWellness(stand("2026-11-03"));
        at(3, 20, 59);
        job.check();

        assertThat(publisher.messages).isEmpty();
    }

    @Test
    void sendsOnceAtTheSummaryTime() {
        job.onWellness(stand("2026-11-03"));
        at(3, 21, 0);
        job.check();
        at(3, 21, 1);
        job.check();
        job.onWellness(new WellnessUpdated(new Values("2026-11-03", 51.0, 60.0, -9.0, 3.5), null));

        assertThat(publisher.messages).hasSize(1);
        assertThat(publisher.topics).containsExactly("cycling/delivery/request/fitness");
        assertThat(publisher.messages.getFirst().messageId()).isEqualTo("fitness-daily-2026-11-03");
    }

    @Test
    void staleStandIsNotSentButALaterOneIs() {
        job.onWellness(stand("2026-11-02"));
        at(3, 21, 0);
        job.check();
        at(3, 21, 15);
        job.check();
        assertThat(publisher.messages).isEmpty();

        job.onWellness(stand("2026-11-03"));

        assertThat(publisher.messages).hasSize(1);
        assertThat(publisher.messages.getFirst().messageId()).isEqualTo("fitness-daily-2026-11-03");
    }

    @Test
    void withoutAnyStandNothingIsSent() {
        at(3, 22, 0);
        job.check();

        assertThat(publisher.messages).isEmpty();
    }

    @Test
    void startAfterTheSummaryTimeSendsAsSoonAsTheRetainedStandArrives() {
        at(3, 21, 30);
        job.onWellness(stand("2026-11-03"));

        assertThat(publisher.messages).hasSize(1);
    }

    @Test
    void failedPublishIsRetriedOnTheNextCheck() {
        job.onWellness(stand("2026-11-03"));
        publisher.failing = true;
        at(3, 21, 0);
        job.check();
        assertThat(publisher.messages).isEmpty();

        publisher.failing = false;
        at(3, 21, 1);
        job.check();

        assertThat(publisher.messages).hasSize(1);
    }

    @Test
    void sendsAgainOnTheNextDay() {
        job.onWellness(stand("2026-11-03"));
        at(3, 21, 0);
        job.check();

        at(4, 0, 30);
        job.onWellness(stand("2026-11-04"));
        assertThat(publisher.messages).hasSize(1);

        at(4, 21, 0);
        job.check();

        assertThat(publisher.messages).extracting(DeliveryMessage::messageId)
                .containsExactly("fitness-daily-2026-11-03", "fitness-daily-2026-11-04");
    }

    @Test
    void yesterdaysStandIsNotSentAfterMidnight() {
        job.onWellness(stand("2026-11-03"));
        at(4, 0, 5);
        job.check();
        at(4, 21, 0);
        job.check();

        assertThat(publisher.messages).isEmpty();
    }

    @Test
    void ignoresUpdatesWithoutToday() {
        at(3, 21, 0);
        job.onWellness(new WellnessUpdated(null, null));
        job.onWellness(new WellnessUpdated(new Values(null, 1.0, 1.0, 0.0, 0.0), null));

        assertThat(publisher.messages).isEmpty();
    }
}
