package com.cycling.report.analysis;

import com.cycling.types.ActivityId;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ActivityAnalysisRepository extends JpaRepository<ActivityAnalysis, Long> {

    Optional<ActivityAnalysis> findByActivityId(ActivityId activityId);

    List<ActivityAnalysis> findTop3ByOrderByActivityDateDesc();

    List<ActivityAnalysis> findTop10ByOrderByActivityDateDesc();

    /**
     * Als Text gelesen: Bei Rueckgabetyp {@code List<ActivityId>} wuerde Spring Data die Abfrage als
     * DTO-Projektion ({@code SELECT new ActivityId(..)}) umschreiben, und das scheitert am Converter.
     */
    @Query(value = "SELECT activity_id FROM activity_analysis", nativeQuery = true)
    List<String> findAllActivityIdValues();

    default List<ActivityId> findAllActivityIds() {
        return findAllActivityIdValues().stream().map(ActivityId::of).toList();
    }
}

