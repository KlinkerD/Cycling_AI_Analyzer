package com.cycling.training.form;

import java.util.Optional;

/**
 * Einordnung der Ramp (CTL-Aenderung pro Woche). Zwischen -5 und +6 (jeweils einschliesslich) ist
 * alles im gruenen Bereich, dann gibt es keinen Hinweis. Dieselbe Einordnung gilt fuer die
 * Fitness-Zusammenfassung und die Tagesempfehlung: ab "zuegiger Aufbau" wird die Empfehlung gebremst.
 */
public enum RampAssessment {

    TOO_FAST("⚠️ Aufbau zu schnell", "#ef5350",
            "Verletzungs- und Überlastungsrisiko – Umfang oder Intensität etwas zurücknehmen."),
    FAST("⚠️ Zügiger Aufbau", "#ff9800",
            "Noch vertretbar, aber im Auge behalten."),
    DECLINING("📉 Fitness baut ab", "#42a5f5",
            "Die Fitness sinkt deutlich – gewollt (Pause, Tapering) oder Zeit für mehr Training?");

    /** Darueber gilt der Aufbau als zuegig (CTL/Woche). */
    static final double FAST_ABOVE = 6;
    /** Darueber gilt der Aufbau als zu schnell (CTL/Woche). */
    static final double TOO_FAST_ABOVE = 8;
    /** Darunter baut die Fitness deutlich ab (CTL/Woche). */
    static final double DECLINING_BELOW = -5;

    private final String title;
    private final String color;
    private final String advice;

    RampAssessment(String title, String color, String advice) {
        this.title = title;
        this.color = color;
        this.advice = advice;
    }

    /** Leer, wenn die Ramp unauffaellig ist oder fehlt. */
    public static Optional<RampAssessment> of(Double rampRate) {
        if (rampRate == null) return Optional.empty();
        if (rampRate > TOO_FAST_ABOVE) return Optional.of(TOO_FAST);
        if (rampRate > FAST_ABOVE) return Optional.of(FAST);
        if (rampRate < DECLINING_BELOW) return Optional.of(DECLINING);
        return Optional.empty();
    }

    /** true, wenn die Fitness schneller als gesund aufgebaut wird ({@link #FAST} oder {@link #TOO_FAST}). */
    public static boolean buildingTooFast(Double rampRate) {
        return of(rampRate).filter(r -> r == FAST || r == TOO_FAST).isPresent();
    }

    public String title() {
        return title;
    }

    public String color() {
        return color;
    }

    public String advice() {
        return advice;
    }
}
