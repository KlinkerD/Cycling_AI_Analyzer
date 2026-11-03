package com.cycling.delivery;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    List<Delivery> findByMessageIdAndStatus(String messageId, DeliveryStatus status);

    List<Delivery> findByStatusAndQueuedAtAfter(DeliveryStatus status, LocalDateTime after);

    @Transactional
    long deleteByQueuedAtBefore(LocalDateTime cutoff);
}
