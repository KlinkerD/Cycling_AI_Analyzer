package com.cycling.ai;

import com.cycling.messaging.contract.AnalysisRequest;
import com.cycling.messaging.contract.RecommendationTextRequest;
import com.cycling.messaging.contract.WorkoutData;
import com.cycling.messaging.contract.ZoneShare;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/** Baut die Gemini-Prompts aus den MQTT-Auftraegen. Rein funktional, ohne I/O. */
public class PromptBuilder {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public String analysisPrompt(AnalysisRequest request) {
        WorkoutData a = request.workout();
        StringBuilder sb = new StringBuilder();

        sb.append("Analysiere dieses Radsport-Training und erstelle einen strukturierten Bericht auf Deutsch.\n\n");
        sb.append("ATHLETEN-SCHWELLENWERTE:\n");
        boolean indoor = Boolean.TRUE.equals(a.indoor());
        sb.append("- FTP: ").append(request.ftp()).append(" W (").append(indoor ? "Indoor" : "Outdoor").append("-FTP)\n");
        sb.append("- Max-HR: ").append(request.maxHr()).append(" bpm\n\n");
        sb.append("AKTIVITÄTSDATEN:\n");
        sb.append("- Name: ").append(a.activityName()).append("\n");
        sb.append("- Fahrtart: ").append(indoor ? "Indoor (Rolle / Smart-Trainer)" : "Outdoor").append("\n");
        sb.append("- Datum: ").append(formatDate(a.activityDate(), "unbekannt")).append("\n");
        sb.append("- Distanz: ").append(String.format("%.1f", orZero(a.distanceKm()))).append(" km\n");
        sb.append("- Dauer: ").append(formatDuration(a.durationSeconds())).append("\n");

        if (a.avgPowerWatts() != null) {
            sb.append("- Durchschnittsleistung: ").append(String.format("%.0f", a.avgPowerWatts())).append(" W\n");
        }
        if (a.maxPowerWatts() != null) {
            sb.append("- Maximale Leistung: ").append(String.format("%.0f", a.maxPowerWatts())).append(" W\n");
        }
        if (a.avgHeartRate() != null) {
            sb.append("- Durchschn. Herzfrequenz: ").append(String.format("%.0f", a.avgHeartRate())).append(" bpm\n");
        }
        if (a.maxHeartRate() != null) {
            sb.append("- Max. Herzfrequenz: ").append(String.format("%.0f", a.maxHeartRate())).append(" bpm\n");
        }
        if (a.avgSpeedKmh() != null) {
            sb.append("- Durchschnittsgeschwindigkeit: ").append(String.format("%.1f", a.avgSpeedKmh())).append(" km/h\n");
        }
        if (a.elevationGainM() != null) {
            sb.append("- Höhenmeter: ").append(String.format("%.0f", a.elevationGainM())).append(" m\n");
        }
        if (a.avgCadenceRpm() != null) {
            sb.append("- Durchschn. Trittfrequenz: ").append(String.format("%.0f", a.avgCadenceRpm())).append(" rpm\n");
        }

        sb.append("\nLEISTUNGSZONEN-VERTEILUNG (FTP-basiert):\n");
        for (ZoneShare z : request.powerZones()) {
            sb.append(String.format("- %s (%s): %.1f%%\n", z.zoneName(), z.zoneLabel(), z.percentage()));
        }

        sb.append("\nHERZFREQUENZZONEN-VERTEILUNG:\n");
        for (ZoneShare z : request.hrZones()) {
            sb.append(String.format("- %s (%s): %.1f%%\n", z.zoneName(), z.zoneLabel(), z.percentage()));
        }

        sb.append("\nVERGLEICH MIT DEN LETZTEN 3 TRAININGS:\n");
        sb.append("Vergleiche Leistungen relativ zur jeweiligen FTP (IF), nicht in absoluten Watt: ")
          .append("Indoor- und Outdoor-Fahrten haben unterschiedliche FTP-Werte.\n");
        if (request.previous().isEmpty()) {
            sb.append("- Keine Vergleichsdaten vorhanden (erstes analysiertes Training)\n");
        } else {
            for (WorkoutData prev : request.previous()) {
                sb.append(String.format("- %s (%s) %s: %.1f km, %.0f W avg",
                        prev.activityName(),
                        formatDate(prev.activityDate(), "?"),
                        ftpContextLabel(prev),
                        orZero(prev.distanceKm()),
                        orZero(prev.avgPowerWatts())));
                if (prev.avgHeartRate() != null) {
                    sb.append(String.format(", %.0f bpm", prev.avgHeartRate()));
                }
                if (prev.overallScore() != null) {
                    sb.append(String.format(", Score: %d/10", prev.overallScore()));
                }
                sb.append("\n");
            }
        }

        sb.append("""

                WICHTIGE ANTWORTREGELN:
                - Schreibe präzise auf Deutsch, konkret auf diese Einheit bezogen.
                - Sei ausführlich: jede Textsektion soll 4-7 kurze Zeilen enthalten.
                - Struktur: nutze Zeilenumbrüche und klare Überschriften pro Feld.
                - Format in Textfeldern:
                  1) Eine kurze Überschrift als reine Textzeile (z. B. "Vergleich mit früheren Trainingseinheiten")
                  2) 3-5 Bullet-Points mit "- "
                  3) Optional eine Zeile "Empfehlung: ..."
                - Nutze KEINE Meta-Bezeichner wie "ÜBERSCHRIFT:", "TITLE:", "BULLETS:" oder ähnliche Präfixe.
                - Bei fehlenden Daten keine Halluzinationen; stattdessen kurz transparent erwähnen.

                Antworte NUR mit einem JSON-Objekt in folgendem Format (kein Markdown-Block, nur reines JSON):
                {
                  "score": <Zahl 1-10>,
                  "strengths": "<Strukturierte Stärken mit Überschrift + Bullet-Points>",
                  "anomalies": "<Strukturierte Auffälligkeiten mit Überschrift + Bullet-Points>",
                  "progress": "<Strukturierter Vergleich zu den letzten Trainings>",
                  "summary": "<Strukturiertes Fazit + konkrete nächste Schritte>"
                }
                """);

        return sb.toString();
    }

    public String recommendationPrompt(RecommendationTextRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("Du bist ein Radsport-Trainer. Formuliere eine kurze Tagesempfehlung auf Deutsch.\n\n");
        sb.append("Die Trainingskategorie steht bereits fest und darf NICHT geaendert werden: ")
                .append(request.categoryLabel()).append("\n");
        sb.append(String.format(Locale.GERMAN, "Form (TSB): %.0f\n", request.form()));
        sb.append(String.format(Locale.GERMAN, "Ramp Rate (CTL-Aufbau pro Woche): %.1f\n", request.rampRate()));
        if (request.rampCapped()) {
            sb.append("HINWEIS: Der Athlet baut schnell auf – die Empfehlung wurde bewusst etwas zurueckhaltender gewaehlt. Erwaehne das kurz.\n");
        }
        sb.append("\nTrainingsverlauf der letzten 7 Tage:\n");
        String last7 = request.last7DaysSummary();
        sb.append(last7 == null || last7.isBlank() ? "- Keine Aktivitaeten erfasst.\n" : last7);
        sb.append("""

                REGELN:
                - Antworte mit 1-2 kurzen Saetzen, direkt an den Athleten gerichtet ("du").
                - Beziehe dich konkret auf Form und die feststehende Kategorie, gib einen praktischen Hinweis.
                - Nenne die Kategorie nicht woertlich als Label, sondern beschreibe die Einheit natuerlich.
                - KEINE Aufzaehlungen, KEIN Markdown, KEINE Ueberschriften – nur Fliesstext.
                """);
        return sb.toString();
    }

    /** z. B. "[Indoor, FTP 235 W, IF 0.85]"; fehlende Angaben (alte Analysen) werden ausgelassen. */
    private static String ftpContextLabel(WorkoutData a) {
        StringBuilder label = new StringBuilder("[");
        label.append(a.indoor() == null ? "Fahrtart unbekannt" : a.indoor() ? "Indoor" : "Outdoor");
        if (a.ftpUsed() != null) {
            label.append(", FTP ").append(a.ftpUsed()).append(" W");
        }
        if (a.intensityFactor() != null) {
            label.append(String.format(Locale.ROOT, ", IF %.2f", a.intensityFactor()));
        }
        return label.append("]").toString();
    }

    private static String formatDate(String isoDateTime, String fallback) {
        if (isoDateTime == null || isoDateTime.isBlank()) return fallback;
        try {
            return LocalDateTime.parse(isoDateTime).format(DATE_FMT);
        } catch (DateTimeParseException e) {
            return isoDateTime;
        }
    }

    private static double orZero(Double value) {
        return value != null ? value : 0;
    }

    private static String formatDuration(Integer seconds) {
        if (seconds == null) return "unbekannt";
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        return String.format("%d:%02d:%02d h", h, m, s);
    }
}
