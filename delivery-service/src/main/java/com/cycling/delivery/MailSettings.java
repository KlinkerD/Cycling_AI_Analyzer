package com.cycling.delivery;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Konfiguration fuer den Mail-Versand (delivery.mail.*).
 * fallbackUsername = spring.mail.username, wird als Absender genommen wenn from leer ist.
 *
 * @param destinations logischer Zielname -> Empfaengeradresse; leere Werte werden ignoriert
 */
public record MailSettings(boolean enabled, String from, String fallbackUsername,
                           String fromName, Map<String, String> destinations, String subjectPrefix) {

    public MailSettings {
        Map<String, String> cleaned = new LinkedHashMap<>();
        if (destinations != null) {
            destinations.forEach((name, address) -> {
                if (address != null && !address.isBlank()) cleaned.put(name.trim(), address);
            });
        }
        destinations = Map.copyOf(cleaned);
    }
}
