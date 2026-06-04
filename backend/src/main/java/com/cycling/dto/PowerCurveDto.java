package com.cycling.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Power-Kurve einer einzelnen Aktivitaet (auf Standard-Bucket-Schema).
 */
public record PowerCurveDto(
        String activityId,
        String activityName,
        LocalDateTime activityDate,
        List<PowerCurvePointDto> points
) {}

