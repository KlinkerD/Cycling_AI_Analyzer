package com.cycling.recommendation;

import com.cycling.intervals.api.IntervalsActivity;
import com.cycling.intervals.api.IntervalsWellnessPoint;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Regeln der Tagesempfehlung, ohne Ein- und Ausgabe.
 *
 * <p>Die Kategorie kommt aus der Form (TSB) des juengsten Wellness-Punkts. Der Ramp-Rate-Waechter
 * (CTL-Aufbau &gt; {@value #RAMP_THRESHOLD} CTL/Woche) stuft sie um eine Stufe Richtung Erholung herunter.
 */
public final class RecommendationCalculator {

    /** Ramp-Rate-Obergrenze in CTL/Woche, ab der nachhaltig aufgebaut werden sollte. */
    static final double RAMP_THRESHOLD = 6.0;
    /** So viele Tage Wellness werden geladen: genug, um sicher einen ~7 Tage alten CTL-Wert zu finden. */
    public static final int WELLNESS_DAYS = 21;
    /** Fenster fuer die Fahrten im KI-Prompt. */
    public static final int SUMMARY_DAYS = 7;

    static final String INSUFFICIENT_DATA_TEXT = "Es liegen noch keine ausreichenden Fitness-Daten (CTL/ATL) vor, "
            + "um eine Empfehlung zu geben. Sobald Trainings erfasst sind, erscheint hier ein Vorschlag.";

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("dd.MM.");

    private RecommendationCalculator() {
    }

    /** @param wellness aufsteigend nach Datum */
    public static Assessment assess(List<IntervalsWellnessPoint> wellness) {
        IntervalsWellnessPoint latest = latestWithCtl(wellness);
        if (latest == null || latest.form() == null) {
            return Assessment.insufficientData();
        }
        double form = latest.form();
        Double rampRate = rampRate(wellness, latest);
        RecommendationCategory base = RecommendationCategory.fromForm(form);
        boolean rampCapped = rampRate != null && rampRate > RAMP_THRESHOLD && base.getLevel() > 0;
        RecommendationCategory category = rampCapped ? base.softer() : base;
        return new Assessment(category, round(form, 0), rampRate == null ? null : round(rampRate, 1), rampCapped,
                parseDate(latest.date()));
    }

    public static String fallbackText(Assessment assessment) {
        String base = switch (assessment.category()) {
            case HARD_INTERVALS -> "Deine Form ist sehr frisch – heute ist eine harte Einheit mit Intervallen oder einem intensiven Block gut machbar.";
            case TEMPO -> "Du bist frisch genug für einen intensiven Reiz – Tempo- bzw. Sweet-Spot-Arbeit passt heute.";
            case ENDURANCE -> "Deine Form ist neutral – heute eignet sich eine ruhige Grundlageneinheit im GA1/GA2-Bereich.";
            case EASY -> "Du bist leicht ermüdet – halte es heute locker und fahre nur regenerativ.";
            case REST -> "Deine Form deutet auf deutliche Ermüdung hin – heute besser Pause oder maximal lockeres Ausrollen.";
            case INSUFFICIENT_DATA -> INSUFFICIENT_DATA_TEXT;
        };
        if (assessment.rampCapped()) {
            base += " Du baust gerade schnell auf – heute bewusst etwas zurückhaltender.";
        }
        return base;
    }

    /** Zeilen "- dd.MM. Name, TSS, IF, km", neueste zuerst; leer, wenn keine Fahrten. */
    public static String last7DaysSummary(List<IntervalsActivity> activities) {
        StringBuilder sb = new StringBuilder();
        activities.stream()
                .sorted(Comparator.comparing(
                        (IntervalsActivity a) -> a.startDateLocal() == null ? "" : a.startDateLocal()).reversed())
                .forEach(a -> {
                    sb.append("- ").append(day(a.startDateLocal())).append(' ').append(a.name());
                    if (a.trainingLoad() != null) {
                        sb.append(String.format(Locale.GERMAN, ", %.0f TSS", a.trainingLoad()));
                    }
                    if (a.intensityFactor() != null) {
                        sb.append(String.format(Locale.GERMAN, ", IF %.2f", a.intensityFactor()));
                    }
                    if (a.distance() != null) {
                        sb.append(String.format(Locale.GERMAN, ", %.0f km", a.distance() / 1000.0));
                    }
                    sb.append('\n');
                });
        return sb.toString();
    }

    private static String day(String startDateLocal) {
        try {
            return LocalDateTime.parse(startDateLocal).format(DAY_FMT);
        } catch (RuntimeException e) {
            return "?.?.";
        }
    }

    /** Juengster Punkt mit vorhandenem CTL (Liste ist aufsteigend nach Datum sortiert). */
    private static IntervalsWellnessPoint latestWithCtl(List<IntervalsWellnessPoint> wellness) {
        IntervalsWellnessPoint latest = null;
        for (IntervalsWellnessPoint p : wellness) {
            if (p.ctl() != null) latest = p;
        }
        return latest;
    }

    /** Ramp Rate = CTL des juengsten Punkts - CTL des Punkts, der ~7 Tage davor liegt. */
    private static Double rampRate(List<IntervalsWellnessPoint> wellness, IntervalsWellnessPoint latest) {
        LocalDate target = parseDate(latest.date()).minusDays(7);
        IntervalsWellnessPoint ref = null;
        long best = Long.MAX_VALUE;
        for (IntervalsWellnessPoint p : wellness) {
            if (p.ctl() == null) continue;
            long diff = Math.abs(ChronoUnit.DAYS.between(parseDate(p.date()), target));
            if (diff < best) {
                best = diff;
                ref = p;
            }
        }
        if (ref == null || best > 10) return null; // kein brauchbarer Referenzpunkt
        return latest.ctl() - ref.ctl();
    }

    private static LocalDate parseDate(String date) {
        return LocalDate.parse(date.length() > 10 ? date.substring(0, 10) : date);
    }

    private static double round(double v, int digits) {
        double f = Math.pow(10, digits);
        return Math.round(v * f) / f;
    }
}
