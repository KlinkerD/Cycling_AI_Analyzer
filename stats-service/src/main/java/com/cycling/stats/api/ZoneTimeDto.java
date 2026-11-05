package com.cycling.stats.api;

import com.cycling.web.ApiNonNull;

/**
 * Summierte Zeit in einer Zone.
 *
 * @param zone       z. B. "Z2"
 * @param label      Name der Zone laut intervals.icu, sonst ""
 * @param seconds    Summe in Sekunden
 * @param percentage Anteil an der Zeit aller Zonen in Prozent, eine Nachkommastelle
 * @param color      Hex-Farbe; null, wenn unbekannt
 */
public record ZoneTimeDto(
        @ApiNonNull String zone,
        @ApiNonNull String label,
        long seconds,
        double percentage,
        String color
) {
}
