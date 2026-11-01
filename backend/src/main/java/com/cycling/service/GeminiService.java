package com.cycling.service;

import com.cycling.dto.ZoneDistributionDto;
import com.cycling.model.ActivityAnalysis;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GeminiService(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.model:gemini-2.5-flash}")
    private String model;

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={key}";

    /**
     * Analysiert ein Radtraining via Google Gemini und gibt ein strukturiertes Ergebnis zurück.
     */
    public AnalysisResult analyzeWorkout(
            ActivityAnalysis activity,
            List<ZoneDistributionDto> powerZones,
            List<ZoneDistributionDto> hrZones,
            List<ActivityAnalysis> lastThree,
            int ftp,
            int maxHr
    ) {
        String prompt = buildPrompt(activity, powerZones, hrZones, lastThree, ftp, maxHr);
        log.info("Sende Analyse-Anfrage an Gemini für Aktivität: {}", activity.getActivityName());

        try {
            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(Map.of("text", prompt)))
                    ),
                    "generationConfig", Map.of(
                            "responseMimeType", "application/json",
                            "temperature", 0.3
                    )
            );

            String url = GEMINI_URL
                    .replace("{model}", model)
                    .replace("{key}", apiKey);

            String response = restClient.post()
                    .uri(url)
                    .header("Content-Type", "application/json")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            return parseGeminiResponse(response);
        } catch (Exception e) {
            log.error("Gemini Analyse fehlgeschlagen: {}", e.getMessage(), e);
            throw new RuntimeException("Gemini Analyse fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    /**
     * Formuliert den Begleittext fuer eine Tagesempfehlung. Die Kategorie steht bereits fest
     * (regelbasiert) und darf NICHT veraendert werden – Gemini liefert nur 1-2 motivierende Saetze.
     * Gibt reinen Text zurueck (kein JSON). Wirft bei Fehler, damit der Aufrufer den Fallback nutzt.
     */
    public String phraseRecommendation(
            String categoryLabel,
            double form,
            double rampRate,
            boolean rampCapped,
            String last7DaysSummary
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("Du bist ein Radsport-Trainer. Formuliere eine kurze Tagesempfehlung auf Deutsch.\n\n");
        sb.append("Die Trainingskategorie steht bereits fest und darf NICHT geaendert werden: ")
                .append(categoryLabel).append("\n");
        sb.append(String.format(Locale.GERMAN, "Form (TSB): %.0f\n", form));
        sb.append(String.format(Locale.GERMAN, "Ramp Rate (CTL-Aufbau pro Woche): %.1f\n", rampRate));
        if (rampCapped) {
            sb.append("HINWEIS: Der Athlet baut schnell auf – die Empfehlung wurde bewusst etwas zurueckhaltender gewaehlt. Erwaehne das kurz.\n");
        }
        sb.append("\nTrainingsverlauf der letzten 7 Tage:\n");
        sb.append(last7DaysSummary == null || last7DaysSummary.isBlank()
                ? "- Keine Aktivitaeten erfasst.\n"
                : last7DaysSummary);
        sb.append("""

                REGELN:
                - Antworte mit 1-2 kurzen Saetzen, direkt an den Athleten gerichtet ("du").
                - Beziehe dich konkret auf Form und die feststehende Kategorie, gib einen praktischen Hinweis.
                - Nenne die Kategorie nicht woertlich als Label, sondern beschreibe die Einheit natuerlich.
                - KEINE Aufzaehlungen, KEIN Markdown, KEINE Ueberschriften – nur Fliesstext.
                """);

        try {
            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(Map.of("text", sb.toString())))
                    ),
                    "generationConfig", Map.of("temperature", 0.5)
            );

            String url = GEMINI_URL.replace("{model}", model).replace("{key}", apiKey);

            String response = restClient.post()
                    .uri(url)
                    .header("Content-Type", "application/json")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            String text = root
                    .path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText("").strip();
            if (text.isBlank()) {
                throw new RuntimeException("Gemini lieferte leeren Empfehlungstext");
            }
            return text;
        } catch (Exception e) {
            log.error("Gemini Empfehlungstext fehlgeschlagen: {}", e.getMessage());
            throw new RuntimeException("Gemini Empfehlungstext fehlgeschlagen: " + e.getMessage(), e);
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

    String buildPrompt(
            ActivityAnalysis a,
            List<ZoneDistributionDto> powerZones,
            List<ZoneDistributionDto> hrZones,
            List<ActivityAnalysis> lastThree,
            int ftp,
            int maxHr
    ) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        StringBuilder sb = new StringBuilder();

        sb.append("Analysiere dieses Radsport-Training und erstelle einen strukturierten Bericht auf Deutsch.\n\n");
        sb.append("ATHLETEN-SCHWELLENWERTE:\n");
        boolean indoor = Boolean.TRUE.equals(a.getIndoor());
        sb.append("- FTP: ").append(ftp).append(" W (").append(indoor ? "Indoor" : "Outdoor").append("-FTP)\n");
        sb.append("- Max-HR: ").append(maxHr).append(" bpm\n\n");
        sb.append("AKTIVITÄTSDATEN:\n");
        sb.append("- Name: ").append(a.getActivityName()).append("\n");
        sb.append("- Fahrtart: ").append(indoor ? "Indoor (Rolle / Smart-Trainer)" : "Outdoor").append("\n");
        sb.append("- Datum: ").append(a.getActivityDate() != null ? a.getActivityDate().format(fmt) : "unbekannt").append("\n");
        sb.append("- Distanz: ").append(String.format("%.1f", a.getDistanceKm())).append(" km\n");
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
        for (ZoneDistributionDto z : powerZones) {
            sb.append(String.format("- %s (%s): %.1f%%\n", z.zoneName(), z.zoneLabel(), z.percentage()));
        }

        sb.append("\nHERZFREQUENZZONEN-VERTEILUNG:\n");
        for (ZoneDistributionDto z : hrZones) {
            sb.append(String.format("- %s (%s): %.1f%%\n", z.zoneName(), z.zoneLabel(), z.percentage()));
        }

        sb.append("\nVERGLEICH MIT DEN LETZTEN 3 TRAININGS:\n");
        sb.append("Vergleiche Leistungen relativ zur jeweiligen FTP (IF), nicht in absoluten Watt: ")
          .append("Indoor- und Outdoor-Fahrten haben unterschiedliche FTP-Werte.\n");
        if (lastThree.isEmpty()) {
            sb.append("- Keine Vergleichsdaten vorhanden (erstes analysiertes Training)\n");
        } else {
            for (ActivityAnalysis prev : lastThree) {
                sb.append(String.format("- %s (%s) %s: %.1f km, %.0f W avg",
                        prev.getActivityName(),
                        prev.getActivityDate() != null ? prev.getActivityDate().format(fmt) : "?",
                        ftpContextLabel(prev),
                        prev.getDistanceKm() != null ? prev.getDistanceKm() : 0,
                        prev.getAvgPowerWatts() != null ? prev.getAvgPowerWatts() : 0));
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

    private AnalysisResult parseGeminiResponse(String rawResponse) throws Exception {
        JsonNode root = objectMapper.readTree(rawResponse);
        String text = root
                .path("candidates").get(0)
                .path("content").path("parts").get(0)
                .path("text").asText();

        // Bereinige ggf. Markdown-Fences
        text = text.strip();
        if (text.startsWith("```")) {
            text = text.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").strip();
        }

        JsonNode analysisJson = objectMapper.readTree(text);
        return new AnalysisResult(
                analysisJson.path("score").asInt(5),
                analysisJson.path("strengths").asText(""),
                analysisJson.path("anomalies").asText(""),
                analysisJson.path("progress").asText(""),
                analysisJson.path("summary").asText("")
        );
    }

    private String formatDuration(Integer seconds) {
        if (seconds == null) return "unbekannt";
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        return String.format("%d:%02d:%02d h", h, m, s);
    }

    public record AnalysisResult(
            int score,
            String strengths,
            String anomalies,
            String progress,
            String summary
    ) {}
}

