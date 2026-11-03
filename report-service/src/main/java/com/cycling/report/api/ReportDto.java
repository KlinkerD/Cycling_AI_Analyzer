package com.cycling.report.api;

import com.cycling.web.ApiNonNull;

import java.util.List;

public record ReportDto(
        @ApiNonNull AnalysisDto analysis,
        @ApiNonNull StreamDataDto streams,
        List<ZoneDistributionDto> powerZones,
        List<ZoneDistributionDto> hrZones,
        List<TrendDataDto> trend,
        PowerCurveSetDto powerCurveSet
) {}

