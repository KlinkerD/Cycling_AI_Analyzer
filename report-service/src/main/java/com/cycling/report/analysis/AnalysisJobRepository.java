package com.cycling.report.analysis;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, String> {

    Optional<AnalysisJob> findFirstByActivityIdAndStatusOrderByCreatedAtDesc(String activityId, AnalysisJobStatus status);

    Optional<AnalysisJob> findFirstByActivityIdOrderByCreatedAtDesc(String activityId);

    List<AnalysisJob> findByStatus(AnalysisJobStatus status);

    List<AnalysisJob> findByStatusAndCreatedAtBefore(AnalysisJobStatus status, LocalDateTime before);
}
