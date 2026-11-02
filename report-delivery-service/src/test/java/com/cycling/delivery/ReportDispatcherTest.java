package com.cycling.delivery;

import com.cycling.delivery.report.ReportDto;
import com.cycling.messaging.contract.ReportDeliveryRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportDispatcherTest {

    private final ReportDeliveryService deliveryService = mock(ReportDeliveryService.class);
    private final ReportDto report = ReportFixtures.report("i1");
    private final Function<String, ReportDto> loader = id -> report;
    private final List<Runnable> queued = new ArrayList<>();
    private final Executor executor = queued::add;
    private final ReportDispatcher dispatcher = new ReportDispatcher(deliveryService, loader, executor);

    @Test
    void requestQueuesChannelsImmediatelyAndSendsInBackground() {
        when(deliveryService.enqueue("i1", "r1")).thenReturn(List.of(DeliveryChannel.MAIL, DeliveryChannel.DISCORD));

        List<DeliveryChannel> channels = dispatcher.handle(new ReportDeliveryRequest("r1", "i1"));

        assertThat(channels).containsExactly(DeliveryChannel.MAIL, DeliveryChannel.DISCORD);
        verify(deliveryService, never()).deliverPending(any(), any());
        assertThat(queued).hasSize(1);

        queued.getFirst().run();
        verify(deliveryService).deliverPending(eq("i1"), eq(loader));
    }

    @Test
    void duplicateOrDisabledRequestSchedulesNothing() {
        when(deliveryService.enqueue("i1", "r1")).thenReturn(List.of());

        dispatcher.handle(new ReportDeliveryRequest("r1", "i1"));

        assertThat(queued).isEmpty();
    }

    @Test
    void scheduledRetryRunsOnSameExecutorWithReportLoader() {
        ArgumentCaptor<Function<String, ReportDto>> captor = ArgumentCaptor.captor();

        dispatcher.retryPending();
        verify(deliveryService, never()).retryPending(any());
        queued.getFirst().run();

        verify(deliveryService).retryPending(captor.capture());
        assertThat(captor.getValue().apply("i1")).isSameAs(report);
    }

    @Test
    void failingDeliveryDoesNotKillTheExecutorTask() {
        when(deliveryService.enqueue("i1", "r1")).thenReturn(List.of(DeliveryChannel.MAIL));
        org.mockito.Mockito.doThrow(new IllegalStateException("DB weg"))
                .when(deliveryService).deliverPending(any(), any());

        dispatcher.handle(new ReportDeliveryRequest("r1", "i1"));
        queued.getFirst().run();
    }
}
