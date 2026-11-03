package com.cycling.intervals.api;

/**
 * Zeit einer Aktivitaet in einer Zone.
 *
 * @param zoneName   z. B. "Z2"
 * @param zoneLabel  Name der Zone laut Athleten-Einstellungen, sonst ""
 * @param percentage Anteil an der Gesamtzeit in Prozent, eine Nachkommastelle
 */
public record IntervalsZoneShare(String zoneName, String zoneLabel, int seconds, double percentage, String color) {
}
