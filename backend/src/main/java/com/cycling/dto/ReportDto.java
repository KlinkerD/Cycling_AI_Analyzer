package com.cycling.dto;

import com.cycling.config.ApiNonNull;
import com.cycling.model.ActivityAnalysis;

import java.util.List;

public record ReportDto(
        @ApiNonNull ActivityAnalysis analysis,
        @ApiNonNull StreamDataDto streams,
        List<ZoneDistributionDto> powerZones,
        List<ZoneDistributionDto> hrZones,
        List<TrendDataDto> trend,
        PowerCurveSetDto powerCurveSet
) {}

