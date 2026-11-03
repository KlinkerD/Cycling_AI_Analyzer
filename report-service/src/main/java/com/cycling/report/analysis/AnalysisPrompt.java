package com.cycling.report.analysis;

import com.cycling.report.api.ZoneDistributionDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Prompt fuer die KI-Bewertung einer Fahrt und Auswertung der Antwort. Der ai-service reicht den Prompt
 * nur an Gemini weiter; was eine gute Analyse ausmacht, steht hier. Rein funktional, ohne I/O.
 */
public final class AnalysisPrompt {

    /** Eher sachlich: die Bewertung soll reproduzierbar sein. */
    public static final double TEMPERATURE = 0.3;
    /** Score, wenn die Antwort keinen enthaelt. */
    static final int DEFAULT_SCORE = 5;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final ObjectMapper JSON = new ObjectMapper();

    private AnalysisPrompt() {
    }

    public static String build(PreparedAnalysis prepared) {
        ActivityAnalysis a = prepared.analysis();
        StringBuilder sb = new StringBuilder();

        sb.append("Analysiere dieses Radsport-Training und erstelle einen strukturierten Bericht auf Deutsch.\n\n");
        sb.append("ATHLETEN-SCHWELLENWERTE:\n");
        boolean indoor = Boolean.TRUE.equals(a.getIndoor());
        sb.append("- FTP: ").append(prepared.ftp()).append(" W (").append(indoor ? "Indoor" : "Outdoor").append("-FTP)\n");
        sb.append("- Max-HR: ").append(prepared.maxHr()).append(" bpm\n\n");
        sb.append("AKTIVITÄTSDATEN:\n");
        sb.append("- Name: ").append(a.getActivityName()).append("\n");
        sb.append("- Fahrtart: ").append(indoor ? "Indoor (Rolle / Smart-Trainer)" : "Outdoor").append("\n");
        sb.append("- Datum: ").append(formatDate(a.getActivityDate(), "unbekannt")).append("\n");
        sb.append("- Distanz: ").append(String.format("%.1f", orZero(a.getDistanceKm()))).append(" km\n");
        sb.append("- Dauer: ").append(formatDuration(a.getDurationSeconds())).append("\n");

        if (a.getAvgPowerWatts() != null) {
            sb.append("- Durchschnittsleistung: ").append(String.format("%.0f", a.getAvgPowerWatts())).append(" W\n");
        }
        if (a.getMaxPowerWatts() != null) {
            sb.append("- Maximale Leistung: ").append(String.format("%.0f", a.getMaxPowerWatts())).append(" W\n");
        }
        if (a.getAvgHeartRate() != null) {
            sb.append("- Durchschn. Herzfrequenz: ").append(String.format("%.0f", a.getAvgHeartRate())).append(" bpm\n");
        }
        if (a.getMaxHeartRate() != null) {
            sb.append("- Max. Herzfrequenz: ").append(String.format("%.0f", a.getMaxHeartRate())).append(" bpm\n");
        }
        if (a.getAvgSpeedKmh() != null) {
            sb.append("- Durchschnittsgeschwindigkeit: ").append(String.format("%.1f", a.getAvgSpeedKmh())).append(" km/h\n");
        }
        if (a.getElevationGainM() != null) {
            sb.append("- Höhenmeter: ").append(String.format("%.0f", a.getElevationGainM())).append(" m\n");
        }
        if (a.getAvgCadenceRpm() != null) {
            sb.append("- Durchschn. Trittfrequenz: ").append(String.format("%.0f", a.getAvgCadenceRpm())).append(" rpm\n");
        }

        sb.append("\nLEISTUNGSZONEN-VERTEILUNG (FTP-basiert):\n");
        appendZones(sb, prepared.powerZones());

        sb.append("\nHERZFREQUENZZONEN-VERTEILUNG:\n");
        appendZones(sb, prepared.hrZones());

        sb.append("\nVERGLEICH MIT DEN LETZTEN 3 TRAININGS:\n");
        sb.append("Vergleiche Leistungen relativ zur jeweiligen FTP (IF), nicht in absoluten Watt: ")
          .append("Indoor- und Outdoor-Fahrten haben unterschiedliche FTP-Werte.\n");
        if (prepared.lastThree().isEmpty()) {
            sb.append("- Keine Vergleichsdaten vorhanden (erstes analysiertes Training)\n");
        } else {
            for (ActivityAnalysis prev : prepared.lastThree()) {
                sb.append(String.format("- %s (%s) %s: %.1f km, %.0f W avg",
                        prev.getActivityName(),
                        formatDate(prev.getActivityDate(), "?"),
                        ftpContextLabel(prev),
                        orZero(prev.getDistanceKm()),
                        orZero(prev.getAvgPowerWatts())));
                if (prev.getAvgHeartRate() != null) {
                    sb.append(String.format(", %.0f bpm", prev.getAvgHeartRate()));
                }
                if (prev.getOverallScore() != null) {
                    sb.append(String.format(", Score: %d/10", prev.getOverallScore()));
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

    /**
     * Liest die JSON-Antwort; ein umschliessender Markdown-Block (```json ... ```) wird toleriert.
     *
     * @throws IllegalArgumentException wenn die Antwort kein JSON-Objekt ist
     */
    public static AiAssessment parse(String text) {
        String json = text == null ? "" : text.strip();
        if (json.startsWith("```")) {
            json = json.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").strip();
        }
        JsonNode node;
        try {
            node = JSON.readTree(json);
        } catch (IOException e) {
            throw new IllegalArgumentException("KI-Antwort ist kein gueltiges JSON: " + e.getMessage(), e);
        }
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("KI-Antwort ist kein JSON-Objekt");
        }
        return new AiAssessment(
                node.path("score").asInt(DEFAULT_SCORE),
                node.path("strengths").asText(""),
                node.path("anomalies").asText(""),
                node.path("progress").asText(""),
                node.path("summary").asText(""));
    }

    private static void appendZones(StringBuilder sb, List<ZoneDistributionDto> zones) {
        for (ZoneDistributionDto z : zones) {
            sb.append(String.format("- %s (%s): %.1f%%\n", z.zoneName(), z.zoneLabel(), z.percentage()));
        }
    }

    /** z. B. "[Indoor, FTP 235 W, IF 0.85]"; fehlende Angaben (alte Analysen) werden ausgelassen. */
    private static String ftpContextLabel(ActivityAnalysis a) {
        StringBuilder label = new StringBuilder("[");
        label.append(a.getIndoor() == null ? "Fahrtart unbekannt" : a.getIndoor() ? "Indoor" : "Outdoor");
        if (a.getFtpUsed() != null) {
            label.append(", FTP ").append(a.getFtpUsed()).append(" W");
        }
        if (a.getIntensityFactor() != null) {
            label.append(String.format(Locale.ROOT, ", IF %.2f", a.getIntensityFactor()));
        }
        return label.append("]").toString();
    }

    private static String formatDate(LocalDateTime date, String fallback) {
        return date == null ? fallback : date.format(DATE_FMT);
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
