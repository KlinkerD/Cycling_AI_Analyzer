package com.cycling.stats.store;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Zeit einer Fahrt in einer Zone, so wie intervals.icu sie mit den Zonen vom Tag der Fahrt berechnet hat.
 *
 * @param zoneName  z. B. "Z2"
 * @param zoneLabel Name laut Athleten-Einstellungen, sonst ""
 * @param color     Hex-Farbe
 */
@Embeddable
public record ZoneTime(
        @Column(name = "zone_name", nullable = false) String zoneName,
        @Column(name = "zone_label") String zoneLabel,
        @Column(name = "seconds", nullable = false) int seconds,
        @Column(name = "color") String color
) {
}
