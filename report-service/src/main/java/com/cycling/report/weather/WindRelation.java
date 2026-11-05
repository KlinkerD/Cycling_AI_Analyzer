package com.cycling.report.weather;

/**
 * Wind relativ zur Fahrtrichtung, fuer den Prompt. Sektoren zu je 90 Grad: Wind von vorn (+-45 Grad um die
 * Fahrtrichtung) = Gegenwind, von hinten = Rueckenwind, dazwischen Seitenwind von links bzw. rechts.
 */
public enum WindRelation {

    HEADWIND("Gegenwind"),
    TAILWIND("Rückenwind"),
    CROSSWIND_LEFT("Seitenwind von links"),
    CROSSWIND_RIGHT("Seitenwind von rechts");

    private final String label;

    WindRelation(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /**
     * @param courseDeg   Fahrtrichtung (0 = Nord, 90 = Ost)
     * @param windFromDeg Richtung, AUS der der Wind kommt
     * @return null, wenn eine der Richtungen fehlt
     */
    public static WindRelation of(Double courseDeg, Double windFromDeg) {
        if (courseDeg == null || windFromDeg == null) return null;
        // Winkel des Windursprungs relativ zur Fahrtrichtung, -180..180; positiv = von rechts
        double relative = ((windFromDeg - courseDeg) % 360 + 540) % 360 - 180;
        double abs = Math.abs(relative);
        if (abs <= 45) return HEADWIND;
        if (abs >= 135) return TAILWIND;
        return relative > 0 ? CROSSWIND_RIGHT : CROSSWIND_LEFT;
    }
}
