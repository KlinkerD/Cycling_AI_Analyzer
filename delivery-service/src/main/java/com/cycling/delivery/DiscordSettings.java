package com.cycling.delivery;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Konfiguration fuer den Discord-Versand (delivery.discord.*).
 *
 * @param destinations logischer Zielname -> Discord-Channel-ID; leere Werte werden ignoriert
 */
public record DiscordSettings(boolean enabled, String botToken, Map<String, String> destinations, String apiBaseUrl) {

    public static final String DEFAULT_API_BASE_URL = "https://discord.com/api/v10";

    public DiscordSettings {
        Map<String, String> cleaned = new LinkedHashMap<>();
        if (destinations != null) {
            destinations.forEach((name, id) -> {
                if (id != null && !id.isBlank()) cleaned.put(name.trim(), id.trim());
            });
        }
        destinations = Map.copyOf(cleaned);
    }
}
