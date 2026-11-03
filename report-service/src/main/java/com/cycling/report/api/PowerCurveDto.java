package com.cycling.report.api;

import com.cycling.web.ApiNonNull;
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

