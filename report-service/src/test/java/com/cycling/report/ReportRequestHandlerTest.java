package com.cycling.report;

import com.cycling.messaging.MessagePublisher;
import com.cycling.messaging.MessagingException;
import com.cycling.messaging.Topics;
import com.cycling.messaging.contract.DeliveryMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.ArrayList;
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

class ReportRequestHandlerTest {

    private static final List<Duration> DELAYS =
            List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15));

    private record Scheduled(Runnable task, Duration delay) {
    }

    private final List<Scheduled> scheduled = new ArrayList<>();
    private final ReportRequestHandler.Scheduler scheduler = (task, delay) -> scheduled.add(new Scheduled(task, delay));
    private final ReportChartRenderer renderer = mock(ReportChartRenderer.class);
    private final MessagePublisher publisher = mock(MessagePublisher.class);
    private final ReportRequestHandler handler =
            new ReportRequestHandler(renderer, new ReportMessageBuilder(List.of()), publisher, scheduler, DELAYS);
    private final ReportDelivery request = new ReportDelivery("r1", "i1", ReportFixtures.report("i1"));

    ReportRequestHandlerTest() {
        when(renderer.render(any())).thenReturn(new ReportCharts(List.of()));
    }

    /** Fuehrt die naechste eingeplante Aufgabe aus und liefert deren Verzoegerung. */
    private Duration runNext() {
        Scheduled next = scheduled.removeFirst();
        next.task().run();
        return next.delay();
    }

    @Test
    void requestIsProcessedInBackgroundAndPublishedForDelivery() {
        handler.handle(request);
        verify(publisher, never()).publish(any(), any());

        assertThat(runNext()).isEqualTo(Duration.ZERO);

        ArgumentCaptor<DeliveryMessage> captor = ArgumentCaptor.forClass(DeliveryMessage.class);
        verify(publisher).publish(eq(Topics.DELIVERY_REQUEST_REPORT), captor.capture());
        assertThat(captor.getValue().messageId()).isEqualTo("r1");
        assertThat(captor.getValue().title()).isEqualTo("🚴 Morgenrunde");
        verify(renderer).render(request.report());
        assertThat(scheduled).isEmpty();
    }

    @Test
    void requestWithoutReportIsDroppedWithoutRetry() {
        handler.handle(new ReportDelivery("r1", "i1", null));

        assertThat(scheduled).isEmpty();
        verify(renderer, never()).render(any());
        verify(publisher, never()).publish(any(), any());
    }

    @Test
    void failedRenderingIsRetriedAfterOneFiveAndFifteenMinutesThenGivenUp() {
        when(renderer.render(any())).thenThrow(new IllegalStateException("QuickChart weg"));
        handler.handle(request);

        List<Duration> delays = new ArrayList<>();
        while (!scheduled.isEmpty()) {
            delays.add(runNext());
        }

        assertThat(delays).containsExactly(Duration.ZERO, Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15));
        verify(renderer, times(4)).render(any());
        verify(publisher, never()).publish(any(), any());
    }

    @Test
    void retrySucceedsAfterTemporaryFailure() {
        when(renderer.render(any()))
                .thenThrow(new IllegalStateException("QuickChart down"))
                .thenReturn(new ReportCharts(List.of()));
        handler.handle(request);

        runNext();
        assertThat(runNext()).isEqualTo(Duration.ofMinutes(1));

        verify(publisher).publish(eq(Topics.DELIVERY_REQUEST_REPORT), any());
        assertThat(scheduled).isEmpty();
    }

    @Test
    void failedPublishIsRetriedToo() {
        doThrow(new MessagingException("broker weg", null))
                .doNothing()
                .when(publisher).publish(any(), any());

        handler.handle(request);
        runNext();
        runNext();

        verify(publisher, times(2)).publish(eq(Topics.DELIVERY_REQUEST_REPORT), any());
        assertThat(scheduled).isEmpty();
    }
}
