package com.cycling.dto;

import java.util.List;

/**
 * Zonen-Verteilung einer einzelnen Aktivitaet, basierend auf den von intervals.icu
 * VORBERECHNETEN Zonen-Zeiten (icu_zone_times / icu_hr_zone_times). Dadurch stimmen die
 * Werte im Report exakt mit der intervals.icu-Website ueberein.
 *
 * @param powerZones Leistungszonen Z1-Z7 (ohne Sweet Spot, Summe = 100%); leer wenn keine Power-Daten
 * @param hrZones    Herzfrequenzzonen (alle konfigurierten Zonen, inkl. 0%); leer wenn keine HF-Daten
 */
public record ActivityZonesDto(
        List<ZoneDistributionDto> powerZones,
        List<ZoneDistributionDto> hrZones
) {}
