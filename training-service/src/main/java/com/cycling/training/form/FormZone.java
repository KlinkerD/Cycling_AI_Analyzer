package com.cycling.training.form;

/**
 * Form-Zonen (TSB, absolut) wie bei intervals.icu/TrainingPeaks. Die Untergrenze gehoert jeweils
 * zur Zone selbst: -10,0 ist Grauzone, +5,0 ist Frisch, +25,0 ist Uebergang, -30,0 ist Optimal.
 */
public enum FormZone {

    TRANSITION("⚪", "Übergang", "#90a4ae", "Sehr frisch – ohne Trainingsreiz geht Fitness verloren."),
    FRESH("🔵", "Frisch", "#42a5f5", "Erholt und bereit für harte Einheiten oder ein Rennen."),
    GREY("⚫", "Grauzone", "#9e9e9e", "Wenig Trainingsreiz – weder richtige Erholung noch Aufbau."),
    OPTIMAL("🟢", "Optimal", "#4caf50", "Produktiver Trainingsreiz."),
    HIGH_RISK("🔴", "Hohes Risiko", "#ef5350", "Überlastungsgefahr – Erholung einplanen.");

    private final String icon;
    private final String label;
    private final String color;
    private final String description;

    FormZone(String icon, String label, String color, String description) {
        this.icon = icon;
        this.label = label;
        this.color = color;
        this.description = description;
    }

    public static FormZone of(double form) {
        if (form >= 25) return TRANSITION;
        if (form >= 5) return FRESH;
        if (form >= -10) return GREY;
        if (form >= -30) return OPTIMAL;
        return HIGH_RISK;
    }

    /** z. B. "Optimal" */
    public String label() {
        return label;
    }

    /** z. B. "🟢 Optimal" */
    public String title() {
        return icon + " " + label;
    }

    public String color() {
        return color;
    }

    public String description() {
        return description;
    }
}
