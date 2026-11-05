package com.cycling.report.analysis;

import com.cycling.types.ActivityId;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ActivityAnalysisRepository extends JpaRepository<ActivityAnalysis, Long> {

    Optional<ActivityAnalysis> findByActivityId(ActivityId activityId);

    List<ActivityAnalysis> findTop3ByOrderByActivityDateDesc();

    List<ActivityAnalysis> findTop10ByOrderByActivityDateDesc();

    @org.springframework.data.jpa.repository.Query("SELECT a.activityId FROM ActivityAnalysis a")
    List<ActivityId> findAllActivityIds();
}

