package com.cycling.intervals.api;

/**
 * Pfade der REST-API des intervals-service. Alle Antworten sind JSON (Records in diesem Paket),
 * Fehler kommen als RFC-9457-ProblemDetail mit einem Feld {@code code} (siehe {@link IntervalsErrorCode}).
 * Datumsparameter {@code from}/{@code to} im Format yyyy-MM-dd.
 */
public final class IntervalsApi {

    public static final String BASE = "/api/intervals";

    /** GET ?from&amp;to: Rad-Aktivitaeten (Ride, VirtualRide) -> Liste von {@link IntervalsActivity}. */
    public static final String ACTIVITIES = BASE + "/activities";
    /** GET: Streams einer Aktivitaet -> {@link IntervalsStreams}. */
    public static final String ACTIVITY_STREAMS = BASE + "/activities/{id}/streams";
    /** GET: Zonenzeiten einer Aktivitaet -> {@link IntervalsActivityZones}. */
    public static final String ACTIVITY_ZONES = BASE + "/activities/{id}/zones";
    /** GET: Power-Curve einer Aktivitaet -> {@link IntervalsPowerCurve}. */
    public static final String ACTIVITY_POWER_CURVE = BASE + "/activities/{id}/power-curve";
    /**
     * GET: Schwellenwerte des Athleten -> {@link IntervalsAthleteThresholds}.
     * PUT mit {@link IntervalsThresholdsUpdate}: schreibt FTP, Indoor-FTP, LTHR, HFmax und Gewicht nach
     * intervals.icu, leert die Caches und liefert die neuen Werte; 400 bei unplausiblen Werten.
     */
    public static final String ATHLETE_THRESHOLDS = BASE + "/athlete/thresholds";
    /** GET: Power- und HF-Zonen des Athleten -> {@link IntervalsAthleteZones}. */
    public static final String ATHLETE_ZONES = BASE + "/athlete/zones";
    /** GET ?from&amp;to: Fitness/Fatigue/Form/Ramp pro Tag, aufsteigend -> Liste von {@link IntervalsWellnessPoint}. */
    public static final String WELLNESS = BASE + "/wellness";
    /** POST: leert alle Caches (z. B. nach einer FTP-Aenderung in intervals.icu), 204. */
    public static final String CACHE_EVICT = BASE + "/cache/evict";

    /** Name des ProblemDetail-Felds mit dem {@link IntervalsErrorCode}. */
    public static final String PROBLEM_CODE = "code";

    private IntervalsApi() {
    }
}
