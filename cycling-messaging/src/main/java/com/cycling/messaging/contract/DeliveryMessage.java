package com.cycling.messaging.contract;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Fachlich neutrale Nachricht an den delivery-service (auf {@code Topics.deliveryRequest(quelle)}).
 * Der Absender baut den Inhalt; der delivery-service rendert ihn pro Kanal
 * (Discord: Embeds + Anhang-Nachricht, Mail: HTML mit Inline-Bildern) und kuemmert sich um
 * Zustellung und Wiederholung.
 *
 * @param messageId   eindeutig pro Nachricht (fuer Nachrichten, die es nur einmal pro Tag gibt, aus dem Datum
 *                    abgeleitet: {@code UUID.nameUUIDFromBytes}); dieselbe ID zweimal (MQTT-Neuzustellung) wird nur einmal versendet
 * @param title       Ueberschrift, z. B. "🚴 Morgenrunde" (Pflicht)
 * @param subtitle    optionale Unterzeile, z. B. ein Datum
 * @param color       optionale Akzentfarbe als "#rrggbb"
 * @param fields      kurze Kennzahlen (Name/Wert)
 * @param sections    Textabschnitte mit eigener Ueberschrift und Farbe
 * @param attachments PNG-Bilder (im JSON als Base64)
 * @param footer      optionale Fusszeile
 * @param targets     wohin; leer = Ziel "default" jedes aktiven Kanals
 */
public record DeliveryMessage(
        UUID messageId,
        String title,
        String subtitle,
        String color,
        List<Field> fields,
        List<Section> sections,
        List<Attachment> attachments,
        String footer,
        List<Target> targets
) {

    public DeliveryMessage {
        fields = fields == null ? List.of() : List.copyOf(fields);
        sections = sections == null ? List.of() : List.copyOf(sections);
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
        targets = targets == null ? List.of() : List.copyOf(targets);
    }

    public record Field(String name, String value) {
    }

    public record Section(String title, String text, String color) {
    }

    /**
     * Ein Bild.
     *
     * @param name stabile Kennung, wird Dateiname bzw. Mail-CID (z. B. "speedAltChart")
     */
    public record Attachment(String name, String title, byte[] png) {

        @Override
        public boolean equals(Object o) {
            return o instanceof Attachment(String n, String t, byte[] p)
                    && java.util.Objects.equals(name, n) && java.util.Objects.equals(title, t) && Arrays.equals(png, p);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(name, title, Arrays.hashCode(png));
        }

        @Override
        public String toString() {
            return "Attachment[name=" + name + ", title=" + title + ", png=" + (png == null ? 0 : png.length) + " bytes]";
        }
    }

    /**
     * Ein Ziel: Kanal + logischer Zielname. Die echten Adressen (Discord-Channel-ID, Mail-Adresse)
     * kennt nur der delivery-service aus seiner Konfiguration.
     *
     * @param channel     "MAIL" oder "DISCORD"
     * @param destination z. B. "default", "reports", "alerts"
     */
    public record Target(String channel, String destination) {
    }
}
