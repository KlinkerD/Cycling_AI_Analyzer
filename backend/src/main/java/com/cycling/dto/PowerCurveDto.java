package com.cycling.dto;

import com.cycling.config.ApiNonNull;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Power-Kurve einer einzelnen Aktivitaet (auf Standard-Bucket-Schema).
 */
public record PowerCurveDto(
        @ApiNonNull String activityId,
        String activityName,
        LocalDateTime activityDate,
        List<PowerCurvePointDto> points
) {}

