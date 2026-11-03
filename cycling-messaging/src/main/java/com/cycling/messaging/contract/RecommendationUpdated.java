package com.cycling.messaging.contract;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * recommendation-service -> alle (retained): die aktuelle Tagesempfehlung. Pro Berechnung kommen zwei
 * Nachrichten mit derselben {@code requestId}: zuerst sofort mit Fallback-Text ({@code textPending=true}),
 * dann mit KI-Text oder - wenn die KI ausfaellt - noch einmal mit Fallback ({@code textPending=false}).
 *
 * @param requestId     Korrelation der Berechnung; bei einem Refresh die ID aus {@link RecommendationRefreshRequest}
 * @param category      z. B. "TEMPO" oder "INSUFFICIENT_DATA"
 * @param categoryLabel Anzeigename, z. B. "Tempo / Sweet Spot"
 * @param color         Akzentfarbe "#rrggbb"
 * @param form          Form (TSB) des juengsten Wellness-Punkts; null ohne Daten
 * @param rampRate      CTL heute - CTL vor 7 Tagen; null ohne Referenzpunkt
 * @param rampCapped    Kategorie wegen zu schnellem Aufbau eine Stufe lockerer
 * @param refDate       Tag, auf den sich die Empfehlung bezieht
 * @param computedAt    Zeitpunkt der Berechnung (lokale Zeit des recommendation-service)
 * @param textPending   true, solange der KI-Text noch erwartet wird
 * @param fallbackUsed  true, wenn {@code text} der statische Fallback-Text ist
 */
public record RecommendationUpdated(
        String requestId,
        String category,
        String categoryLabel,
        String color,
        String text,
        Double form,
        Double rampRate,
        boolean rampCapped,
        LocalDate refDate,
        LocalDateTime computedAt,
        boolean textPending,
        boolean fallbackUsed
) {
}
