package com.cycling.delivery;

import com.cycling.messaging.contract.DeliveryMessage;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryDispatcherTest {

    private final DeliveryService deliveryService = mock(DeliveryService.class);
    private final List<Runnable> queued = new ArrayList<>();
    private final Executor executor = queued::add;
    private final DeliveryDispatcher dispatcher = new DeliveryDispatcher(deliveryService, executor);
    private final DeliveryMessage message = MessageFixtures.message("m1");

    @Test
    void messageIsAcceptedWithSourceFromTopicAndSentInBackground() {
        when(deliveryService.accept("fitness", message)).thenReturn(List.of(
                new Delivery("m1", "fitness", DeliveryChannel.DISCORD, "default", LocalDateTime.now())));

        dispatcher.handle("cycling/delivery/request/fitness", message);

        verify(deliveryService, never()).deliverPending(any());
        assertThat(queued).hasSize(1);
        queued.getFirst().run();
        verify(deliveryService).deliverPending("m1");
    }

    @Test
    void duplicateOrUnroutableMessageSchedulesNothing() {
        when(deliveryService.accept("report", message)).thenReturn(List.of());

        dispatcher.handle("cycling/delivery/request/report", message);

        assertThat(queued).isEmpty();
    }

    @Test
    void scheduledRetryAndCleanUpRunOnSameExecutor() {
        dispatcher.retryPending();
        dispatcher.cleanUp();
        verify(deliveryService, never()).retryPending();
        verify(deliveryService, never()).cleanUp();

        queued.forEach(Runnable::run);

        verify(deliveryService).retryPending();
        verify(deliveryService).cleanUp();
    }

    @Test
    void failingDeliveryDoesNotKillTheExecutorTask() {
        when(deliveryService.accept("report", message)).thenReturn(List.of(
                new Delivery("m1", "report", DeliveryChannel.MAIL, "default", LocalDateTime.now())));
        doThrow(new IllegalStateException("DB weg")).when(deliveryService).deliverPending(any());

        dispatcher.handle("cycling/delivery/request/report", message);
        queued.getFirst().run();
    }
}
