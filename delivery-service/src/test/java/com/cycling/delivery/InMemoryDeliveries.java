package com.cycling.delivery;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Einfacher In-Memory-Ersatz fuer Delivery- und MessageRepository (nur die genutzten Methoden). */
final class InMemoryDeliveries {

    final List<Delivery> rows = new ArrayList<>();
    final List<StoredMessage> messages = new ArrayList<>();
    final DeliveryRepository repository = mock(DeliveryRepository.class);
    final MessageRepository messageRepository = mock(MessageRepository.class);

    InMemoryDeliveries() {
        when(repository.save(any(Delivery.class))).thenAnswer(inv -> {
            Delivery d = inv.getArgument(0);
            if (!rows.contains(d)) rows.add(d);
            return d;
        });
        when(repository.findByMessageIdAndStatus(any(), any())).thenAnswer(inv -> rows.stream()
                .filter(d -> d.getMessageId().equals(inv.getArgument(0)) && d.getStatus() == inv.getArgument(1))
                .toList());
        when(repository.findByStatusAndQueuedAtAfter(any(), any())).thenAnswer(inv -> rows.stream()
                .filter(d -> d.getStatus() == inv.getArgument(0)
                        && d.getQueuedAt().isAfter((LocalDateTime) inv.getArgument(1)))
                .toList());
        when(repository.deleteByQueuedAtBefore(any())).thenAnswer(inv -> {
            LocalDateTime cutoff = inv.getArgument(0);
            long before = rows.size();
            rows.removeIf(d -> d.getQueuedAt().isBefore(cutoff));
            return before - rows.size();
        });

        when(messageRepository.save(any(StoredMessage.class))).thenAnswer(inv -> {
            StoredMessage m = inv.getArgument(0);
            if (!messages.contains(m)) messages.add(m);
            return m;
        });
        when(messageRepository.existsById(any())).thenAnswer(inv -> message(inv.getArgument(0)).isPresent());
        when(messageRepository.findById(any())).thenAnswer(inv -> message(inv.getArgument(0)));
        when(messageRepository.clearPayloadsReceivedBefore(any())).thenAnswer(inv -> {
            LocalDateTime cutoff = inv.getArgument(0);
            int count = 0;
            for (StoredMessage m : messages) {
                if (m.getReceivedAt().isBefore(cutoff) && m.getPayload() != null) {
                    m.clearPayload();
                    count++;
                }
            }
            return count;
        });
        when(messageRepository.deleteByReceivedAtBefore(any())).thenAnswer(inv -> {
            LocalDateTime cutoff = inv.getArgument(0);
            long before = messages.size();
            messages.removeIf(m -> m.getReceivedAt().isBefore(cutoff));
            return before - messages.size();
        });
    }

    Delivery add(String messageId, DeliveryChannel channel, String destination, LocalDateTime queuedAt) {
        Delivery d = new Delivery(messageId, "report", channel, destination, queuedAt);
        rows.add(d);
        return d;
    }

    Optional<StoredMessage> message(String messageId) {
        return messages.stream().filter(m -> m.getMessageId().equals(messageId)).findFirst();
    }

    List<Delivery> rowsOf(String messageId) {
        return rows.stream().filter(d -> d.getMessageId().equals(messageId)).toList();
    }
}
