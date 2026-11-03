package com.cycling.training.recommendation;

import com.cycling.training.form.FormZone;

/**
 * Tagesempfehlungs-Kategorie. Die Reihenfolge {@link #level} laeuft von locker (0) nach hart (4);
 * {@code INSUFFICIENT_DATA} ist ein Sonderzustand (-1), wenn keine CTL/ATL-Daten vorliegen.
 *
 * <p>Die Zuordnung folgt deterministisch der Form-Zone ({@link #of(FormZone)}), damit Fitness-Anzeige
 * und Empfehlung dieselbe Skala nutzen. Der Begleittext wird von Gemini formuliert, ohne die hier
 * festgelegte Kategorie zu aendern.
 */
public enum RecommendationCategory {

    REST("Ruhetag", "#9e9e9e", 0),
    EASY("Locker / Regeneration", "#42a5f5", 1),
    ENDURANCE("Grundlage", "#66bb6a", 2),
    TEMPO("Tempo / Sweet Spot", "#ff9800", 3),
    HARD_INTERVALS("Harte Intervalle", "#ef5350", 4),
    INSUFFICIENT_DATA("Nicht genug Daten", "#bdbdbd", -1);

    private final String label;
    private final String color;
    private final int level;

    RecommendationCategory(String label, String color, int level) {
        this.label = label;
        this.color = color;
        this.level = level;
    }

    public String getLabel() {
        return label;
    }

    public String getColor() {
        return color;
    }

    public int getLevel() {
        return level;
    }

    /**
     * Grundkategorie zur Form-Zone:
     * Uebergang und Frisch → harte Intervalle, Grauzone → Tempo, Optimal → Grundlage, Hohes Risiko → Ruhetag.
     * {@code EASY} entsteht nur, wenn der Ramp-Waechter {@code ENDURANCE} eine Stufe herunterstuft.
     */
    public static RecommendationCategory of(FormZone zone) {
        return switch (zone) {
            case TRANSITION, FRESH -> HARD_INTERVALS;
            case GREY -> TEMPO;
            case OPTIMAL -> ENDURANCE;
            case HIGH_RISK -> REST;
        };
    }

    /**
     * Gibt die naechst-lockerere Kategorie zurueck (eine Stufe Richtung Erholung).
     * Wird vom Ramp-Rate-Waechter genutzt, wenn der Aufbau zu schnell ist.
     * Fuer {@code REST} und {@code INSUFFICIENT_DATA} ein No-op.
     */
    public RecommendationCategory softer() {
        if (this == INSUFFICIENT_DATA) return this;
        for (RecommendationCategory c : values()) {
            if (c.level == this.level - 1) return c;
        }
        return this;
    }
}
