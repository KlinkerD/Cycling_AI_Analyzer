package com.cycling.report.weather;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface WeatherPointRepository extends JpaRepository<WeatherPoint, Long> {

    List<WeatherPoint> findByActivityIdOrderByMinuteOfRideAsc(String activityId);

    @Modifying
    @Transactional
    @Query("DELETE FROM WeatherPoint w WHERE w.activityId = :activityId")
    void deleteByActivityId(@Param("activityId") String activityId);
}
