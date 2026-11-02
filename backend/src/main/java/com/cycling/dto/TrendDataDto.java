package com.cycling.dto;

import com.cycling.config.ApiNonNull;
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

