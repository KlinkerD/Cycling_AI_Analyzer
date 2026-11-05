package com.cycling.report.analysis;

import com.cycling.types.ActivityId;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, UUID> {

    Optional<AnalysisJob> findFirstByActivityIdAndStatusOrderByCreatedAtDesc(ActivityId activityId, AnalysisJobStatus status);

    Optional<AnalysisJob> findFirstByActivityIdOrderByCreatedAtDesc(ActivityId activityId);

    List<AnalysisJob> findByStatus(AnalysisJobStatus status);

    List<AnalysisJob> findByStatusAndCreatedAtBefore(AnalysisJobStatus status, LocalDateTime before);
}
