package com.cycling.intervals.api;

import java.util.List;

/**
 * Power-Curve (Mean Maximal Power) einer Aktivitaet, aufsteigend nach Dauer. Leer, wenn intervals.icu
 * keine liefert (z. B. Fahrt ohne Leistungsmesser).
 */
public record IntervalsPowerCurve(List<Point> points) {

    /** Beste Durchschnittsleistung ueber {@code seconds} Sekunden. */
    public record Point(int seconds, int watts) {
    }
}
