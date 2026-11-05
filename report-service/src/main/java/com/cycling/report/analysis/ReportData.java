package com.cycling.report.analysis;

import com.cycling.report.api.StreamDataDto;
import com.cycling.report.api.ZoneBandDto;
import com.cycling.report.api.ZoneDistributionDto;

import java.util.List;

/**
 * Die intervals.icu-Daten eines Reports, die nicht in {@link ActivityAnalysis} stehen. Werden bei der Analyse
 * (oder per "Daten neu laden") gespeichert, damit ein Report ohne intervals-service angezeigt und versendet
 * werden kann.
 *
 * @param powerBands Leistungszonen-Grenzen fuer die Karte, mit der FTP der Fahrt
 * @param hrBands    HF-Zonen-Grenzen fuer die Karte
 */
public record ReportData(
        StreamDataDto streams,
        List<ZoneDistributionDto> powerZones,
        List<ZoneDistributionDto> hrZones,
        List<ZoneBandDto> powerBands,
        List<ZoneBandDto> hrBands
) {

    public ReportData {
        powerZones = powerZones == null ? List.of() : List.copyOf(powerZones);
        hrZones = hrZones == null ? List.of() : List.copyOf(hrZones);
        powerBands = powerBands == null ? List.of() : List.copyOf(powerBands);
        hrBands = hrBands == null ? List.of() : List.copyOf(hrBands);
    }
}
