package com.cycling.repository;

import com.cycling.model.ActivityAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ActivityAnalysisRepository extends JpaRepository<ActivityAnalysis, Long> {

    Optional<ActivityAnalysis> findByActivityId(String activityId);

    List<ActivityAnalysis> findTop3ByOrderByActivityDateDesc();

    List<ActivityAnalysis> findTop10ByOrderByActivityDateDesc();

    @org.springframework.data.jpa.repository.Query("SELECT a.activityId FROM ActivityAnalysis a")
    List<String> findAllActivityIds();
}

