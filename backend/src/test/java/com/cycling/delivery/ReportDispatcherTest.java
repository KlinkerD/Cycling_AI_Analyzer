package com.cycling.delivery;

import com.cycling.dto.ReportDto;
import com.cycling.service.AnalysisService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportDispatcherTest {

    private final ReportDeliveryService deliveryService = mock(ReportDeliveryService.class);
    private final AnalysisService analysisService = mock(AnalysisService.class);
    private final List<Runnable> queued = new ArrayList<>();
    private final Executor executor = queued::add;
    private final ReportDispatcher dispatcher = new ReportDispatcher(deliveryService, analysisService, executor);
    private final ReportDto report = ReportFixtures.report("i1");

    @Test
    void dispatchQueuesChannelsImmediatelyAndSendsInBackground() {
        when(deliveryService.enqueue("i1")).thenReturn(List.of(DeliveryChannel.MAIL, DeliveryChannel.DISCORD));

        List<DeliveryChannel> channels = dispatcher.dispatch(report);

        assertThat(channels).containsExactly(DeliveryChannel.MAIL, DeliveryChannel.DISCORD);
        verify(deliveryService, never()).deliver(report);
        assertThat(queued).hasSize(1);

        queued.getFirst().run();
        var order = inOrder(deliveryService);
        order.verify(deliveryService).enqueue("i1");
        order.verify(deliveryService).deliver(report);
    }

    @Test
    void scheduledRetryRunsOnSameExecutorAndLoadsReportsFromAnalysisService() {
        when(analysisService.getReport("i1")).thenReturn(report);
        org.mockito.ArgumentCaptor<java.util.function.Function<String, ReportDto>> loader =
                org.mockito.ArgumentCaptor.captor();

        dispatcher.retryPending();
        verify(deliveryService, never()).retryPending(org.mockito.ArgumentMatchers.any());
        queued.getFirst().run();

        verify(deliveryService).retryPending(loader.capture());
        assertThat(loader.getValue().apply("i1")).isSameAs(report);
    }

    @Test
    void resendWithoutActiveChannelDoesNotEvenLoadTheReport() {
        when(deliveryService.enabledChannels()).thenReturn(List.of());

        List<DeliveryChannel> channels = dispatcher.resend("i1");

        assertThat(channels).isEmpty();
        verify(analysisService, never()).getReport("i1");
        assertThat(queued).isEmpty();
    }

    @Test
    void resendLoadsStoredReportAndDispatchesIt() {
        when(deliveryService.enabledChannels()).thenReturn(List.of(DeliveryChannel.DISCORD));
        when(deliveryService.enqueue("i1")).thenReturn(List.of(DeliveryChannel.DISCORD));
        when(analysisService.getReport("i1")).thenReturn(report);

        List<DeliveryChannel> channels = dispatcher.resend("i1");

        assertThat(channels).containsExactly(DeliveryChannel.DISCORD);
        queued.getFirst().run();
        verify(deliveryService).deliver(report);
    }
}
