package com.cycling.report.api;

import com.cycling.web.ApiNonNull;
import java.time.LocalDateTime;

public record TrendDataDto(
        @ApiNonNull String activityId,
        String activityName,
        LocalDateTime activityDate,
        Double avgPowerWatts,
        Double avgHeartRate,
        Double distanceKm,
        Double avgSpeedKmh,
        Integer overallScore
) {}

