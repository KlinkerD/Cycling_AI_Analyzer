package com.cycling.training.recommendation;

import java.util.Locale;

/**
 * Prompt fuer den Begleittext der Tagesempfehlung. Der ai-service reicht ihn nur an Gemini weiter;
 * die Antwort ist reiner Fliesstext und wird unveraendert uebernommen. Rein funktional, ohne I/O.
 */
public final class RecommendationPrompt {

    /** Etwas freier formuliert als die Fahrt-Analyse. */
    public static final double TEMPERATURE = 0.5;

    private RecommendationPrompt() {
    }

    /** @param last7DaysSummary Zeilen "- dd.MM. Name, TSS, IF, km" (leer = keine Fahrten) */
    public static String build(Assessment assessment, String last7DaysSummary) {
        StringBuilder sb = new StringBuilder();
        sb.append("Du bist ein Radsport-Trainer. Formuliere eine kurze Tagesempfehlung auf Deutsch.\n\n");
        sb.append("Die Trainingskategorie steht bereits fest und darf NICHT geaendert werden: ")
                .append(assessment.category().getLabel()).append("\n");
        sb.append(String.format(Locale.GERMAN, "Form (TSB): %.0f", assessment.form()));
        if (assessment.formZone() != null) {
            sb.append(" – Zone \"").append(assessment.formZone().label()).append("\" (")
                    .append(assessment.formZone().description()).append(")");
        }
        sb.append("\n");
        double ramp = assessment.rampRate() != null ? assessment.rampRate() : 0.0;
        sb.append(String.format(Locale.GERMAN, "Ramp Rate (CTL-Aufbau pro Woche): %.1f\n", ramp));
        if (assessment.rampCapped()) {
            sb.append("HINWEIS: Der Athlet baut schnell auf – die Empfehlung wurde bewusst etwas zurueckhaltender gewaehlt. Erwaehne das kurz.\n");
        }
        sb.append("\nTrainingsverlauf der letzten 7 Tage:\n");
        sb.append(last7DaysSummary == null || last7DaysSummary.isBlank()
                ? "- Keine Aktivitaeten erfasst.\n" : last7DaysSummary);
        sb.append("""

                REGELN:
                - Antworte mit 1-2 kurzen Saetzen, direkt an den Athleten gerichtet ("du").
                - Beziehe dich konkret auf Form und die feststehende Kategorie, gib einen praktischen Hinweis.
                - Nenne die Kategorie nicht woertlich als Label, sondern beschreibe die Einheit natuerlich.
                - KEINE Aufzaehlungen, KEIN Markdown, KEINE Ueberschriften – nur Fliesstext.
                """);
        return sb.toString();
    }
}
