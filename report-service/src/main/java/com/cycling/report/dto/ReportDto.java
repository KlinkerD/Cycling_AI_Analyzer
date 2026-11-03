package com.cycling.report.dto;



import java.util.List;

public record ReportDto(
        ActivityAnalysis analysis,
        StreamDataDto streams,
        List<ZoneDistributionDto> powerZones,
        List<ZoneDistributionDto> hrZones,
        List<TrendDataDto> trend,
        PowerCurveSetDto powerCurveSet
) {}

