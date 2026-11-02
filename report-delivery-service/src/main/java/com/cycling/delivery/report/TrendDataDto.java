package com.cycling.delivery.report;

import java.time.LocalDateTime;

public record TrendDataDto(
        String activityId,
        String activityName,
        LocalDateTime activityDate,
        Double avgPowerWatts,
        Double avgHeartRate,
        Double distanceKm,
        Double avgSpeedKmh,
        Integer overallScore
) {}

