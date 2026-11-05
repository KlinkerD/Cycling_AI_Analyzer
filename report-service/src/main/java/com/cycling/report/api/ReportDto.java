package com.cycling.report.api;

import com.cycling.web.ApiNonNull;

import java.util.List;

/**
 * Report einer analysierten Fahrt.
 *
 * @param powerZoneBands Leistungszonen Z1-Z7 in Watt, mit der FTP der Fahrt ({@code ftpUsed}); fuer die Karte
 * @param hrZoneBands    Herzfrequenzzonen in bpm (aktuelle Grenzen); fuer die Karte
 */
public record ReportDto(
        @ApiNonNull AnalysisDto analysis,
        @ApiNonNull StreamDataDto streams,
        List<ZoneDistributionDto> powerZones,
        List<ZoneDistributionDto> hrZones,
        List<TrendDataDto> trend,
        PowerCurveSetDto powerCurveSet,
        List<ZoneBandDto> powerZoneBands,
        List<ZoneBandDto> hrZoneBands
) {}
