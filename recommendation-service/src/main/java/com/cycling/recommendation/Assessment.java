package com.cycling.recommendation;

import java.time.LocalDate;

/**
 * Deterministisches Ergebnis der Empfehlung, noch ohne Text.
 *
 * @param form       Form (TSB), auf ganze Zahlen gerundet; null ohne Daten
 * @param rampRate   CTL-Anstieg pro Woche, eine Nachkommastelle; null ohne Referenzpunkt
 * @param rampCapped Kategorie wegen zu schnellem Aufbau eine Stufe lockerer
 * @param refDate    Tag des juengsten Wellness-Punkts mit CTL
 */
public record Assessment(RecommendationCategory category, Double form, Double rampRate, boolean rampCapped,
                         LocalDate refDate) {

    public static Assessment insufficientData() {
        return new Assessment(RecommendationCategory.INSUFFICIENT_DATA, null, null, false, null);
    }
}
