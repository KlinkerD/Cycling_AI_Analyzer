package com.cycling.recommendation;

/**
 * Tagesempfehlungs-Kategorie. Die Reihenfolge {@link #level} laeuft von locker (0) nach hart (4);
 * {@code INSUFFICIENT_DATA} ist ein Sonderzustand (-1), wenn keine CTL/ATL-Daten vorliegen.
 *
 * <p>Die Zuordnung erfolgt deterministisch ueber die Form (TSB), siehe {@link #fromForm(double)}.
 * Der Begleittext wird von Gemini formuliert, ohne die hier festgelegte Kategorie zu aendern.
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
     * Leitet die Grundkategorie aus der Form (TSB) ab.
     * Grenzen: &gt;+15 hart, +5..+15 Tempo, -10..+5 Grundlage, -25..-10 locker, &lt;-25 Ruhetag.
     */
    public static RecommendationCategory fromForm(double form) {
        if (form > 15) return HARD_INTERVALS;
        if (form > 5) return TEMPO;
        if (form > -10) return ENDURANCE;
        if (form > -25) return EASY;
        return REST;
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
