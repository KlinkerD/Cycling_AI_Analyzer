package com.cycling.stats.api;

import com.cycling.web.ApiNonNull;

/**
 * Laufendes Jahr ab dem 1.1. bis heute, dazu derselbe Zeitraum im Vorjahr zum Vergleich.
 *
 * @param year           laufendes Jahr
 * @param current        1.1. bis heute
 * @param previousYear   1.1. bis zum selben Tag im Vorjahr (29.2. wird zum 28.2.)
 * @param importRunning  true, solange der Erstimport laeuft; die Zahlen sind dann noch unvollstaendig
 */
public record YearToDateDto(
        int year,
        @ApiNonNull StatsDto current,
        @ApiNonNull StatsDto previousYear,
        boolean importRunning
) {
}
