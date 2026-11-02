package com.cycling.delivery;

import com.cycling.delivery.report.ReportDto;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportDeliveryServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 12, 0);
    private final Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);

    private final InMemoryDeliveries deliveries = new InMemoryDeliveries();
    private final ReportChartRenderer renderer = mock(ReportChartRenderer.class);
    private final ReportSender mail = sender(DeliveryChannel.MAIL, true);
    private final ReportSender discord = sender(DeliveryChannel.DISCORD, true);
    private final ReportDto report = ReportFixtures.report("i1");

    private ReportDeliveryService service(ReportSender... senders) {
        return new ReportDeliveryService(List.of(senders), deliveries.repository, renderer, clock);
    }

    private static ReportSender sender(DeliveryChannel channel, boolean enabled) {
        ReportSender s = mock(ReportSender.class);
        when(s.channel()).thenReturn(channel);
        when(s.isEnabled()).thenReturn(enabled);
        return s;
    }

    @Test
    void enqueueCreatesPendingRowsOnlyForEnabledChannels() {
        ReportSender disabledDiscord = sender(DeliveryChannel.DISCORD, false);

        List<DeliveryChannel> channels = service(mail, disabledDiscord).enqueue("i1", "r1");

        assertThat(channels).containsExactly(DeliveryChannel.MAIL);
        assertThat(deliveries.rows).singleElement().satisfies(d -> {
            assertThat(d.getChannel()).isEqualTo(DeliveryChannel.MAIL);
            assertThat(d.getStatus()).isEqualTo(DeliveryStatus.PENDING);
            assertThat(d.getQueuedAt()).isEqualTo(NOW);
        });
    }

    @Test
    void enabledChannelsListsOnlyActiveSenders() {
        assertThat(service(mail, sender(DeliveryChannel.DISCORD, false)).enabledChannels())
                .containsExactly(DeliveryChannel.MAIL);
    }

    @Test
    void enqueueResetsExistingRowInsteadOfAddingASecondOne() {
        ReportDelivery old = deliveries.add("i1", DeliveryChannel.DISCORD, NOW.minusDays(10));
        old.setStatus(DeliveryStatus.FAILED);
        old.setAttempts(5);
        old.setCompletedParts(1);
        old.setLastError("boom");

        service(discord).enqueue("i1", "r1");

        assertThat(deliveries.rows).singleElement().satisfies(d -> {
            assertThat(d.getStatus()).isEqualTo(DeliveryStatus.PENDING);
            assertThat(d.getAttempts()).isZero();
            assertThat(d.getCompletedParts()).isZero();
            assertThat(d.getLastError()).isNull();
            assertThat(d.getQueuedAt()).isEqualTo(NOW);
        });
    }

    @Test
    void deliverMarksPendingRowAsSentAfterSuccess() throws Exception {
        ReportCharts charts = new ReportCharts(List.of());
        when(renderer.render(report)).thenReturn(charts);
        ReportDelivery row = deliveries.add("i1", DeliveryChannel.MAIL, NOW);

        service(mail).deliver(report);

        org.mockito.Mockito.verify(mail).send(report, charts, row);
        assertThat(row.getStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThat(row.getAttempts()).isEqualTo(1);
        assertThat(row.getLastAttemptAt()).isEqualTo(NOW);
    }

    @Test
    void temporaryFailureStaysPendingWithError() throws Exception {
        when(renderer.render(report)).thenReturn(new ReportCharts(List.of()));
        ReportDelivery row = deliveries.add("i1", DeliveryChannel.MAIL, NOW);
        org.mockito.Mockito.doThrow(DeliveryException.temporary("SMTP down", null))
                .when(mail).send(any(), any(), any());

        service(mail).deliver(report);

        assertThat(row.getStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(row.getAttempts()).isEqualTo(1);
        assertThat(row.getLastError()).isEqualTo("SMTP down");
    }

    @Test
    void givesUpAfterFifthTemporaryFailure() throws Exception {
        when(renderer.render(report)).thenReturn(new ReportCharts(List.of()));
        ReportDelivery row = deliveries.add("i1", DeliveryChannel.MAIL, NOW);
        row.setAttempts(4);
        org.mockito.Mockito.doThrow(DeliveryException.temporary("SMTP down", null))
                .when(mail).send(any(), any(), any());

        service(mail).deliver(report);

        assertThat(row.getAttempts()).isEqualTo(5);
        assertThat(row.getStatus()).isEqualTo(DeliveryStatus.FAILED);
    }

    @Test
    void permanentFailureFailsImmediately() throws Exception {
        when(renderer.render(report)).thenReturn(new ReportCharts(List.of()));
        ReportDelivery row = deliveries.add("i1", DeliveryChannel.DISCORD, NOW);
        org.mockito.Mockito.doThrow(DeliveryException.permanent("HTTP 401 (Bot-Token ungueltig)", null))
                .when(discord).send(any(), any(), any());

        service(discord).deliver(report);

        assertThat(row.getAttempts()).isEqualTo(1);
        assertThat(row.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(row.getLastError()).contains("401");
    }

    @Test
    void unexpectedErrorInOneChannelCountsAsTemporaryAndOtherChannelStillGoesOut() throws Exception {
        when(renderer.render(report)).thenReturn(new ReportCharts(List.of()));
        ReportDelivery mailRow = deliveries.add("i1", DeliveryChannel.MAIL, NOW);
        ReportDelivery discordRow = deliveries.add("i1", DeliveryChannel.DISCORD, NOW);
        org.mockito.Mockito.doThrow(new IllegalArgumentException("kaputt"))
                .when(mail).send(any(), any(), any());

        service(mail, discord).deliver(report);

        assertThat(mailRow.getStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(mailRow.getLastError()).contains("kaputt");
        assertThat(discordRow.getStatus()).isEqualTo(DeliveryStatus.SENT);
    }

    @Test
    void leavesRowsOfDisabledChannelUntouched() throws Exception {
        when(renderer.render(report)).thenReturn(new ReportCharts(List.of()));
        ReportSender disabledDiscord = sender(DeliveryChannel.DISCORD, false);
        ReportDelivery row = deliveries.add("i1", DeliveryChannel.DISCORD, NOW);

        service(mail, disabledDiscord).deliver(report);

        org.mockito.Mockito.verify(disabledDiscord, org.mockito.Mockito.never()).send(any(), any(), any());
        assertThat(row.getStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(row.getAttempts()).isZero();
    }

    @Test
    void rendersChartsOnceForAllChannels() {
        when(renderer.render(report)).thenReturn(new ReportCharts(List.of()));
        deliveries.add("i1", DeliveryChannel.MAIL, NOW);
        deliveries.add("i1", DeliveryChannel.DISCORD, NOW);

        service(mail, discord).deliver(report);

        org.mockito.Mockito.verify(renderer, org.mockito.Mockito.times(1)).render(report);
    }

    @Test
    void retryPendingResendsOnlyRowsQueuedWithinTwoDays() throws Exception {
        ReportDto oldReport = ReportFixtures.report("old");
        when(renderer.render(any())).thenReturn(new ReportCharts(List.of()));
        ReportDelivery recent = deliveries.add("i1", DeliveryChannel.MAIL, NOW.minusHours(47));
        ReportDelivery tooOld = deliveries.add("old", DeliveryChannel.MAIL, NOW.minusDays(2).minusMinutes(1));
        java.util.Map<String, ReportDto> reports = java.util.Map.of("i1", report, "old", oldReport);

        service(mail).retryPending(reports::get);

        assertThat(recent.getStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThat(tooOld.getStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(tooOld.getAttempts()).isZero();
    }

    @Test
    void reportThatCannotBeLoadedCountsAsFailedAttemptAndOthersContinue() {
        when(renderer.render(any())).thenReturn(new ReportCharts(List.of()));
        ReportDelivery broken = deliveries.add("broken", DeliveryChannel.MAIL, NOW.minusHours(1));
        ReportDelivery fine = deliveries.add("i1", DeliveryChannel.MAIL, NOW.minusHours(1));

        service(mail).retryPending(id -> {
            if (id.equals("broken")) throw new IllegalStateException("intervals.icu nicht erreichbar");
            return report;
        });

        assertThat(broken.getStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(broken.getAttempts()).isEqualTo(1);
        assertThat(broken.getLastError()).contains("intervals.icu nicht erreichbar");
        assertThat(fine.getStatus()).isEqualTo(DeliveryStatus.SENT);
    }

    @Test
    void sameRequestDeliveredTwiceDoesNotRequeueSentRow() {
        service(mail).enqueue("i1", "r1");
        deliveries.rows.getFirst().setStatus(DeliveryStatus.SENT);

        List<DeliveryChannel> channels = service(mail).enqueue("i1", "r1");

        assertThat(channels).isEmpty();
        assertThat(deliveries.rows.getFirst().getStatus()).isEqualTo(DeliveryStatus.SENT);
    }

    @Test
    void newRequestRequeuesSentRow() {
        service(mail).enqueue("i1", "r1");
        deliveries.rows.getFirst().setStatus(DeliveryStatus.SENT);

        assertThat(service(mail).enqueue("i1", "r2")).containsExactly(DeliveryChannel.MAIL);
        assertThat(deliveries.rows.getFirst().getStatus()).isEqualTo(DeliveryStatus.PENDING);
    }

    @Test
    void deliverPendingDoesNotLoadReportWhenNothingIsOpen() {
        java.util.concurrent.atomic.AtomicInteger loads = new java.util.concurrent.atomic.AtomicInteger();

        service(mail).deliverPending("i1", id -> {
            loads.incrementAndGet();
            return report;
        });

        assertThat(loads).hasValue(0);
    }

    @Test
    void deliverPendingLoadsAndSendsOpenRows() throws Exception {
        when(renderer.render(any())).thenReturn(new ReportCharts(List.of()));
        ReportDelivery row = deliveries.add("i1", DeliveryChannel.MAIL, NOW);

        service(mail).deliverPending("i1", id -> report);

        assertThat(row.getStatus()).isEqualTo(DeliveryStatus.SENT);
    }
}
