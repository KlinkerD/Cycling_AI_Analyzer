package com.cycling.report;

import com.cycling.messaging.MessagePublisher;
import com.cycling.messaging.MessagingException;
import com.cycling.messaging.Topics;
import com.cycling.messaging.contract.DeliveryMessage;
import com.cycling.messaging.contract.ReportDeliveryRequest;
import com.cycling.report.dto.ReportDto;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    private final ReportDto report = ReportFixtures.report("i1");
    private final AtomicInteger loads = new AtomicInteger();

    ReportRequestHandlerTest() {
        when(renderer.render(any())).thenReturn(new ReportCharts(List.of()));
    }

    private ReportRequestHandler handler(Function<String, ReportDto> loader) {
        return new ReportRequestHandler(id -> {
            loads.incrementAndGet();
            return loader.apply(id);
        }, renderer, new ReportMessageBuilder(List.of()), publisher, scheduler, DELAYS);
    }

    /** Fuehrt die naechste eingeplante Aufgabe aus und liefert deren Verzoegerung. */
    private Duration runNext() {
        Scheduled next = scheduled.removeFirst();
        next.task().run();
        return next.delay();
    }

    @Test
    void requestIsProcessedInBackgroundAndPublishedForDelivery() {
        handler(id -> report).handle(new ReportDeliveryRequest("r1", "i1"));
        verify(publisher, never()).publish(any(), any());

        assertThat(runNext()).isEqualTo(Duration.ZERO);

        ArgumentCaptor<DeliveryMessage> captor = ArgumentCaptor.forClass(DeliveryMessage.class);
        verify(publisher).publish(eq(Topics.DELIVERY_REQUEST_REPORT), captor.capture());
        assertThat(captor.getValue().messageId()).isEqualTo("r1");
        assertThat(captor.getValue().title()).isEqualTo("🚴 Morgenrunde");
        assertThat(scheduled).isEmpty();
    }

    @Test
    void failedLoadIsRetriedAfterOneFiveAndFifteenMinutesThenGivenUp() {
        handler(id -> {
            throw new IllegalStateException("backend weg");
        }).handle(new ReportDeliveryRequest("r1", "i1"));

        List<Duration> delays = new ArrayList<>();
        while (!scheduled.isEmpty()) {
            delays.add(runNext());
        }

        assertThat(delays).containsExactly(Duration.ZERO, Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15));
        assertThat(loads).hasValue(4);
        verify(publisher, never()).publish(any(), any());
    }

    @Test
    void retrySucceedsAfterTemporaryFailure() {
        AtomicInteger calls = new AtomicInteger();
        handler(id -> {
            if (calls.incrementAndGet() == 1) throw new IllegalStateException("QuickChart down");
            return report;
        }).handle(new ReportDeliveryRequest("r1", "i1"));

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

        handler(id -> report).handle(new ReportDeliveryRequest("r1", "i1"));
        runNext();
        runNext();

        verify(publisher, org.mockito.Mockito.times(2)).publish(eq(Topics.DELIVERY_REQUEST_REPORT), any());
        assertThat(scheduled).isEmpty();
    }
}
