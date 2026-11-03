package com.cycling.fitness;

import java.util.Optional;

/**
 * Einordnung der Ramp (CTL-Aenderung pro Woche). Zwischen -5 und +6 (jeweils einschliesslich) ist
 * alles im gruenen Bereich, dann gibt es keinen Hinweis. Die 6 entspricht dem Ramp-Waechter der
 * Tagesempfehlung im Backend.
 */
public enum RampAssessment {

    TOO_FAST("⚠️ Aufbau zu schnell", "#ef5350",
            "Verletzungs- und Überlastungsrisiko – Umfang oder Intensität etwas zurücknehmen."),
    FAST("⚠️ Zügiger Aufbau", "#ff9800",
            "Noch vertretbar, aber im Auge behalten."),
    DECLINING("📉 Fitness baut ab", "#42a5f5",
            "Die Fitness sinkt deutlich – gewollt (Pause, Tapering) oder Zeit für mehr Training?");

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
        if (rampRate > 8) return Optional.of(TOO_FAST);
        if (rampRate > 6) return Optional.of(FAST);
        if (rampRate < -5) return Optional.of(DECLINING);
        return Optional.empty();
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
