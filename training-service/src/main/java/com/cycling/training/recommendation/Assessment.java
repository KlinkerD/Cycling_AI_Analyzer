package com.cycling.training.recommendation;

import com.cycling.training.form.FormZone;

import java.time.LocalDate;

/**
 * Deterministisches Ergebnis der Empfehlung, noch ohne Text.
 *
 * @param formZone   Form-Zone des juengsten Wellness-Punkts; null ohne Daten
 * @param form       Form (TSB), auf ganze Zahlen gerundet; null ohne Daten
 * @param rampRate   CTL-Anstieg pro Woche, eine Nachkommastelle; null ohne Wert
 * @param rampCapped Kategorie wegen zu schnellem Aufbau eine Stufe lockerer
 * @param refDate    Tag des juengsten Wellness-Punkts mit CTL
 */
public record Assessment(RecommendationCategory category, FormZone formZone, Double form, Double rampRate,
                         boolean rampCapped, LocalDate refDate) {

    public static Assessment insufficientData() {
        return new Assessment(RecommendationCategory.INSUFFICIENT_DATA, null, null, null, false, null);
    }
}
