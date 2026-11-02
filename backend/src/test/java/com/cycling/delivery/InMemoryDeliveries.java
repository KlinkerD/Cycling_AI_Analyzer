package com.cycling.delivery;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Einfacher In-Memory-Ersatz fuer das ReportDeliveryRepository (nur die genutzten Methoden). */
final class InMemoryDeliveries {

    final List<ReportDelivery> rows = new ArrayList<>();
    final ReportDeliveryRepository repository = mock(ReportDeliveryRepository.class);

    InMemoryDeliveries() {
        when(repository.save(any(ReportDelivery.class))).thenAnswer(inv -> {
            ReportDelivery d = inv.getArgument(0);
            if (!rows.contains(d)) rows.add(d);
            return d;
        });
        when(repository.findByActivityIdAndChannel(any(), any())).thenAnswer(inv -> rows.stream()
                .filter(d -> d.getActivityId().equals(inv.getArgument(0)) && d.getChannel() == inv.getArgument(1))
                .findFirst());
        when(repository.findByActivityIdAndStatus(any(), any())).thenAnswer(inv -> rows.stream()
                .filter(d -> d.getActivityId().equals(inv.getArgument(0)) && d.getStatus() == inv.getArgument(1))
                .toList());
        when(repository.findByStatusAndQueuedAtAfter(any(), any())).thenAnswer(inv -> rows.stream()
                .filter(d -> d.getStatus() == inv.getArgument(0)
                        && d.getQueuedAt().isAfter((LocalDateTime) inv.getArgument(1)))
                .toList());
    }

    ReportDelivery add(String activityId, DeliveryChannel channel, LocalDateTime queuedAt) {
        ReportDelivery d = new ReportDelivery(activityId, channel);
        d.setQueuedAt(queuedAt);
        rows.add(d);
        return d;
    }

    Optional<ReportDelivery> find(String activityId, DeliveryChannel channel) {
        return rows.stream().filter(d -> d.getActivityId().equals(activityId) && d.getChannel() == channel).findFirst();
    }
}
