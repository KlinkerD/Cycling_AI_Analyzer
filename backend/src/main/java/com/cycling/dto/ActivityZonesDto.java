package com.cycling.dto;

import java.util.List;

/**
 * Zonen-Verteilung einer einzelnen Aktivitaet, basierend auf den von intervals.icu
 * VORBERECHNETEN Zonen-Zeiten (icu_zone_times / icu_hr_zone_times). Dadurch stimmen die
 * Werte im Report exakt mit der intervals.icu-Website ueberein.
 * <p>
 * Weil dafuer ohnehin die Aktivitaet geladen wird, liefert der Record auch die Angaben
 * mit, die fuer die Wahl der FTP gebraucht werden.
 *
 * @param powerZones Leistungszonen Z1-Z7 (ohne Sweet Spot, Summe = 100%); leer wenn keine Power-Daten
 * @param hrZones    Herzfrequenzzonen (alle konfigurierten Zonen, inkl. 0%); leer wenn keine HF-Daten
 * @param indoor     Indoor-Fahrt (VirtualRide oder Trainer-Flag); null, wenn die Aktivitaet nicht geladen werden konnte
 * @param icuFtp     FTP, mit der intervals.icu die Aktivitaet berechnet hat ({@code icu_ftp}); null, wenn unbekannt
 */
public record ActivityZonesDto(
        List<ZoneDistributionDto> powerZones,
        List<ZoneDistributionDto> hrZones,
        Boolean indoor,
        Integer icuFtp
) {}
