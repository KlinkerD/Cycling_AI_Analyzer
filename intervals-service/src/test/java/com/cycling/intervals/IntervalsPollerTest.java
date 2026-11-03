package com.cycling.intervals;

import com.cycling.intervals.api.IntervalsActivity;
import com.cycling.intervals.api.IntervalsErrorCode;
import com.cycling.intervals.api.IntervalsWellnessPoint;
import com.cycling.messaging.MessagePublisher;
import com.cycling.messaging.MessagingException;
import com.cycling.messaging.Topics;
import com.cycling.messaging.contract.ActivityDetected;
import com.cycling.messaging.contract.WellnessUpdated;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntervalsPollerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 3);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-03T10:00:00Z"), ZoneOffset.UTC);

    private final IntervalsGateway gateway = mock(IntervalsGateway.class);
    private final MessagePublisher publisher = mock(MessagePublisher.class);
    private final IntervalsPoller poller = new IntervalsPoller(gateway, publisher, CLOCK, 2);

    private static IntervalsActivity ride(String id) {
        return new IntervalsActivity(id, "Runde " + id, "2026-11-02T08:00:00", "Ride", 30000.0, 3600, 3700,
                180.0, 190.0, 500.0, 140.0, 170.0, 8.0, 200.0, 60.0, 0.72);
    }

    private void recent(IntervalsActivity... rides) {
        when(gateway.activities(TODAY.minusDays(2), TODAY.plusDays(1))).thenReturn(List.of(rides));
    }

    // ---- Aktivitaeten ------------------------------------------------------------------------

    @Test
    void reportsAllActivitiesOfTheWindowWithSummary() {
        recent(ride("i1"), ride("i2"));

        poller.pollActivities();

        ArgumentCaptor<ActivityDetected> sent = ArgumentCaptor.forClass(ActivityDetected.class);
        verify(publisher, times(2)).publish(eq(Topics.INTERVALS_ACTIVITY_DETECTED), sent.capture());
        assertThat(sent.getAllValues()).extracting(ActivityDetected::activityId).containsExactly("i1", "i2");
        assertThat(sent.getValue().normalizedPower()).isEqualTo(190.0);
        assertThat(sent.getValue().movingTime()).isEqualTo(3600);
    }

    /** Wiederholtes Melden ist gewollt: so startet der report-service fehlgeschlagene Analysen neu. */
    @Test
    void reportsAgainOnNextRun() {
        recent(ride("i1"));

        poller.pollActivities();
        poller.pollActivities();

        verify(publisher, times(2)).publish(eq(Topics.INTERVALS_ACTIVITY_DETECTED), any());
    }

    @Test
    void failedPublishDoesNotStopTheOthers() {
        recent(ride("i1"), ride("i2"));
        doThrow(new MessagingException("offline", null))
                .doNothing()
                .when(publisher).publish(eq(Topics.INTERVALS_ACTIVITY_DETECTED), any());

        poller.pollActivities();

        verify(publisher, times(2)).publish(eq(Topics.INTERVALS_ACTIVITY_DETECTED), any());
    }

    @Test
    void intervalsErrorDoesNotStopPoll() {
        when(gateway.activities(any(), any()))
                .thenThrow(new IntervalsApiException(IntervalsErrorCode.INTERVALS_UNAVAILABLE, "weg"));
        when(gateway.wellness(any(), any())).thenReturn(List.of(
                new IntervalsWellnessPoint("2026-11-03", 50.0, 55.0, -5.0, 2.0)));

        poller.poll();

        verify(publisher, never()).publish(any(), any());
        verify(publisher).publishRetained(eq(Topics.INTERVALS_WELLNESS_UPDATED), any());
    }

    // ---- Fitness-Werte -----------------------------------------------------------------------

    private void wellness(IntervalsWellnessPoint... points) {
        when(gateway.wellness(TODAY.minusDays(1), TODAY)).thenReturn(List.of(points));
    }

    @Test
    void publishesTodayRetainedWithPreviousDayAndRoundedValues() {
        wellness(new IntervalsWellnessPoint("2026-11-02", 49.96, 52.0, -2.04, 1.0),
                new IntervalsWellnessPoint("2026-11-03", 50.04, 55.01, -4.97, 2.26));

        poller.pollWellness();

        ArgumentCaptor<WellnessUpdated> sent = ArgumentCaptor.forClass(WellnessUpdated.class);
        verify(publisher).publishRetained(eq(Topics.INTERVALS_WELLNESS_UPDATED), sent.capture());
        assertThat(sent.getValue().today())
                .isEqualTo(new WellnessUpdated.Values("2026-11-03", 50.0, 55.0, -5.0, 2.3));
        assertThat(sent.getValue().previousDay().ctl()).isEqualTo(50.0);
    }

    @Test
    void ignoresChangesBelowOneDecimal() {
        wellness(new IntervalsWellnessPoint("2026-11-03", 50.01, 55.0, -4.99, 2.0));
        poller.pollWellness();
        wellness(new IntervalsWellnessPoint("2026-11-03", 50.04, 55.02, -4.98, 2.01));
        poller.pollWellness();
        wellness(new IntervalsWellnessPoint("2026-11-03", 50.2, 55.0, -4.8, 2.0));
        poller.pollWellness();

        verify(publisher, times(2)).publishRetained(eq(Topics.INTERVALS_WELLNESS_UPDATED), any());
    }

    @Test
    void nothingWithoutValuesForToday() {
        wellness(new IntervalsWellnessPoint("2026-11-02", 50.0, 55.0, -5.0, 2.0));

        poller.pollWellness();

        verify(publisher, never()).publishRetained(any(), any());
    }

    @Test
    void failedWellnessPublishIsRetried() {
        wellness(new IntervalsWellnessPoint("2026-11-03", 50.0, 55.0, -5.0, 2.0));
        doThrow(new MessagingException("offline", null)).doNothing()
                .when(publisher).publishRetained(eq(Topics.INTERVALS_WELLNESS_UPDATED), any());

        poller.pollWellness();
        poller.pollWellness();
        poller.pollWellness();

        verify(publisher, times(2)).publishRetained(eq(Topics.INTERVALS_WELLNESS_UPDATED), any());
    }
}
