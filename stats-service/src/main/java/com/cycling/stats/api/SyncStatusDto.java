package com.cycling.stats.api;

import com.cycling.web.ApiNonNull;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Stand der gespeicherten Fahrten.
 *
 * @param rides             gespeicherte Fahrten
 * @param ridesWithoutZones Fahrten, deren Zonenzeiten noch fehlen (werden beim naechsten Abgleich nachgeholt)
 * @param oldestRide        Datum der aeltesten Fahrt; null ohne Fahrten
 * @param newestRide        Datum der neuesten Fahrt; null ohne Fahrten
 * @param lastSync          letzte Aktualisierung einer Fahrt; null ohne Fahrten
 * @param importStart       ab diesem Tag werden Fahrten importiert
 * @param importRunning     true, solange der Erstimport laeuft
 */
public record SyncStatusDto(
        long rides,
        int ridesWithoutZones,
        LocalDate oldestRide,
        LocalDate newestRide,
        Instant lastSync,
        @ApiNonNull LocalDate importStart,
        boolean importRunning
) {
}
