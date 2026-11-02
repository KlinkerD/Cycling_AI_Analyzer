package com.cycling.messaging.contract;

/** Anteil der Fahrzeit in einer Zone, z. B. ("Z2", "Ausdauer", 41.5). */
public record ZoneShare(String zoneName, String zoneLabel, double percentage) {
}
