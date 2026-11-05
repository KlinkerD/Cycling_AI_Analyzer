package com.cycling.delivery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Seit die messageId eine UUID ist, lassen sich Zeilen mit alten Text-IDs (z. B. "fitness-2026-11-05") nicht mehr
 * laden. Sie werden beim Start einmal geloescht, bevor MQTT und Wiederholungen loslaufen. Verloren geht nur
 * Versand-Historie; offene Versaende alter Nachrichten entfallen.
 */
@Component
class LegacyMessageIdCleanup {

    private static final Logger log = LoggerFactory.getLogger(LegacyMessageIdCleanup.class);
    static final String UUID_PATTERN = "^[0-9a-fA-F]{8}-([0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}$";

    private final JdbcTemplate jdbc;

    LegacyMessageIdCleanup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener(ApplicationStartedEvent.class)
    void removeNonUuidMessages() {
        try {
            int deliveries = jdbc.update("DELETE FROM delivery WHERE NOT REGEXP_LIKE(message_id, ?)", UUID_PATTERN);
            int messages = jdbc.update("DELETE FROM delivery_message WHERE NOT REGEXP_LIKE(message_id, ?)", UUID_PATTERN);
            if (deliveries + messages > 0) {
                log.info("Alte Nachrichten ohne UUID entfernt: {} Nachrichten, {} Versand-Zeilen", messages, deliveries);
            }
        } catch (DataAccessException e) {
            log.warn("Alte Nachrichten-IDs nicht bereinigt: {}", e.getMessage());
        }
    }
}
