package com.cycling.intervals.api;

import java.util.List;

/**
 * Zonenverteilung einer Aktivitaet aus den von intervals.icu vorberechneten Zonenzeiten
 * ({@code icu_zone_times}, {@code icu_hr_zone_times}).
 *
 * @param powerZones Z1-Z7 ohne Sweet Spot (Summe 100 %); leer ohne Leistungsdaten
 * @param hrZones    alle konfigurierten HF-Zonen; leer ohne HF-Daten
 * @param indoor     VirtualRide oder Trainer-Fahrt
 * @param icuFtp     FTP, mit der intervals.icu die Fahrt berechnet hat; null, wenn unbekannt
 */
public record IntervalsActivityZones(List<IntervalsZoneShare> powerZones, List<IntervalsZoneShare> hrZones,
                                     boolean indoor, Integer icuFtp) {
}
