package com.cycling.messaging.contract;

/**
 * Auftrag an den ai-service, den Begleittext einer Tagesempfehlung zu formulieren.
 * Die Kategorie steht fest und darf von der KI nicht geaendert werden.
 *
 * @param last7DaysSummary vorformatierte Zeilen "- dd.MM. Name, TSS, IF, km" (leer = keine Fahrten)
 */
public record RecommendationTextRequest(
        String requestId,
        String categoryLabel,
        double form,
        double rampRate,
        boolean rampCapped,
        String last7DaysSummary
) {
}
