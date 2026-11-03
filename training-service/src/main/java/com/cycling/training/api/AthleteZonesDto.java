package com.cycling.training.api;

import com.cycling.web.ApiNonNull;
import java.util.List;

/**
 * Athleten-Profil aus intervals.icu: Schwellenwerte (Kopfzeile) und Zonen (Overview-Seite).
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

