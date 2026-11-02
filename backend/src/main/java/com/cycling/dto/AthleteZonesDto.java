package com.cycling.dto;

import com.cycling.config.ApiNonNull;
import java.util.List;

/**
 * Athleten-Zonen aus intervals.icu fuer die Overview-Seite.
 *
 * @param thresholds  Schwellenwerte (Name, FTP, LTHR, MaxHR, Gewicht)
 * @param powerZones  Leistungszonen (relativ zu FTP)
 * @param hrZones     Herzfrequenz-Zonen
 * @param hrReference "lthr" oder "max_hr" - Referenzwert der HF-Zonen
 */
public record AthleteZonesDto(
        @ApiNonNull AthleteThresholdsDto thresholds,
        List<ZoneDto> powerZones,
        List<ZoneDto> hrZones,
        @ApiNonNull @io.swagger.v3.oas.annotations.media.Schema(allowableValues = {"lthr", "max_hr"}) String hrReference
) {}

