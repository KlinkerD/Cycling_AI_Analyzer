package com.cycling.fitness.api;

/**
 * Schwellenwerte des Athleten aus den Rad-Einstellungen von intervals.icu. Alle Felder koennen null sein,
 * wenn sie dort nicht eingetragen sind.
 *
 * @param indoorFtp FTP fuer Indoor-Fahrten
 * @param weight    kg
 */
public record AthleteThresholdsDto(
        String name,
        Integer ftp,
        Integer indoorFtp,
        Integer lthr,
        Integer maxHr,
        Double weight
) {
}
