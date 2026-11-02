package com.cycling.delivery;

/**
 * Konfiguration fuer den Mail-Versand (cycling.report.mail.*).
 * fallbackUsername = spring.mail.username, wird als Absender genommen wenn from leer ist.
 */
public record MailSettings(boolean enabled, String from, String fallbackUsername,
                           String fromName, String to, String subjectPrefix) {
}
