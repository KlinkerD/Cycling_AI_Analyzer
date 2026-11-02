package com.cycling.delivery;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReportDeliveryRepository extends JpaRepository<ReportDelivery, Long> {

    Optional<ReportDelivery> findByActivityIdAndChannel(String activityId, DeliveryChannel channel);

    List<ReportDelivery> findByActivityIdAndStatus(String activityId, DeliveryStatus status);

    List<ReportDelivery> findByStatusAndQueuedAtAfter(DeliveryStatus status, LocalDateTime after);
}
