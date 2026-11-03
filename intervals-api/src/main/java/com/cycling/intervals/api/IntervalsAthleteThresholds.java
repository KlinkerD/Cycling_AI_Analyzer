package com.cycling.intervals.api;

/**
 * Schwellenwerte des Athleten aus den Rad-Einstellungen (Ride/VirtualRide) von intervals.icu.
 * Alle Felder koennen null sein, wenn sie dort nicht eingetragen sind.
 *
 * @param indoorFtp FTP fuer Indoor-Fahrten ({@code indoor_ftp})
 * @param weight    kg
 */
public record IntervalsAthleteThresholds(String name, Integer ftp, Integer indoorFtp, Integer lthr, Integer maxHr,
                                         Double weight) {
}
