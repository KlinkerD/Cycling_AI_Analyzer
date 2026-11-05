package com.cycling.report.analysis;

import com.cycling.types.ActivityId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReportSnapshotRepository extends JpaRepository<ReportSnapshot, Long> {

    Optional<ReportSnapshot> findByActivityId(ActivityId activityId);
}
