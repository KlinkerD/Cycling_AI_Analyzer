package com.cycling.report.api;

import com.cycling.web.ApiNonNull;

/**
 * Grenzen einer Zone in Watt bzw. bpm, zum Einfaerben des Fahrtverlaufs auf der Karte.
 *
 * @param lower untere Grenze (einschliesslich)
 * @param upper obere Grenze (ausschliesslich); null = nach oben offen
 */
public record ZoneBandDto(
        @ApiNonNull String name,
        int lower,
        Integer upper,
        @ApiNonNull String color
) {}
