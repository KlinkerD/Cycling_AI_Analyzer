package com.cycling.dto;

import com.cycling.model.ActivityAnalysis;

import java.util.List;

public record ReportDto(
        ActivityAnalysis analysis,
        StreamDataDto streams,
        List<ZoneDistributionDto> powerZones,
        List<ZoneDistributionDto> hrZones,
        List<TrendDataDto> trend
) {}

