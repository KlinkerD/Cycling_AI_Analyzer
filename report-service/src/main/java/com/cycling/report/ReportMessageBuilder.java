package com.cycling.report;

import com.cycling.messaging.contract.DeliveryMessage;
import com.cycling.messaging.contract.DeliveryMessage.Attachment;
import com.cycling.messaging.contract.DeliveryMessage.Field;
import com.cycling.messaging.contract.DeliveryMessage.Section;
import com.cycling.messaging.contract.DeliveryMessage.Target;
import com.cycling.report.dto.ActivityAnalysis;
import com.cycling.report.dto.ReportDto;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Macht aus einem Report eine kanalneutrale {@link DeliveryMessage}: Kennzahlen werden Felder,
 * KI-Texte farbige Abschnitte, Charts Anhaenge. Der Score bestimmt die Akzentfarbe.
 */
public class ReportMessageBuilder {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final List<Target> targets;

    /** @param targets Ziele aus der Konfiguration (report.targets); leer = Default-Ziele des delivery-service */
    public ReportMessageBuilder(List<Target> targets) {
        this.targets = List.copyOf(targets);
    }

    public DeliveryMessage build(String messageId, ReportDto report, ReportCharts charts) {
        ActivityAnalysis a = report.analysis();
        return new DeliveryMessage(
                messageId,
                "🚴 " + (a.getActivityName() == null ? "" : a.getActivityName()),
                a.getActivityDate() != null ? a.getActivityDate().format(DATE_FMT) : null,
                scoreColor(a.getOverallScore()),
                fields(a),
                sections(a),
                charts.all().stream().map(c -> new Attachment(c.key(), c.title(), c.png())).toList(),
                a.getAnalyzedAt() != null ? "Analysiert am " + a.getAnalyzedAt().format(DATETIME_FMT) + " Uhr" : null,
                targets);
    }

    private static List<Field> fields(ActivityAnalysis a) {
        List<Field> fields = new ArrayList<>();
        add(fields, "Score", a.getOverallScore() != null ? a.getOverallScore() + " / 10" : null);
        add(fields, "Distanz", num(a.getDistanceKm(), 1, "km"));
        add(fields, "Dauer", formatDuration(a.getDurationSeconds()));
        add(fields, "Leistung ø", num(a.getAvgPowerWatts(), 0, "W"));
        add(fields, "NP", num(a.getNormalizedPowerWatts(), 0, "W"));
        add(fields, "TSS", num(a.getTrainingLoad(), 0, ""));
        add(fields, "IF", num(a.getIntensityFactor(), 2, ""));
        add(fields, "Puls ø", num(a.getAvgHeartRate(), 0, "bpm"));
        add(fields, "Tempo ø", num(a.getAvgSpeedKmh(), 1, "km/h"));
        add(fields, "Höhenmeter", num(a.getElevationGainM(), 0, "hm"));
        add(fields, "Kadenz ø", num(a.getAvgCadenceRpm(), 0, "rpm"));
        return fields;
    }

    private static List<Section> sections(ActivityAnalysis a) {
        List<Section> sections = new ArrayList<>();
        addSection(sections, "👍 Was gut lief", a.getStrengths(), "#4caf50");
        addSection(sections, "⚠️ Auffälligkeiten", a.getAnomalies(), "#ff9800");
        addSection(sections, "📈 Fortschritt", a.getProgress(), "#2196f3");
        addSection(sections, "📝 Fazit", a.getSummary(), "#3f51b5");
        return sections;
    }

    private static void add(List<Field> fields, String name, String value) {
        if (value != null) fields.add(new Field(name, value));
    }

    private static void addSection(List<Section> sections, String title, String text, String color) {
        if (text != null && !text.isBlank()) sections.add(new Section(title, text, color));
    }

    static String scoreColor(Integer score) {
        if (score == null) return "#9e9e9e";
        if (score >= 8) return "#4caf50";
        if (score >= 5) return "#ff9800";
        return "#f44336";
    }

    private static String num(Double v, int digits, String unit) {
        if (v == null) return null;
        String n = String.format(Locale.GERMAN, "%." + digits + "f", v);
        return unit.isEmpty() ? n : n + " " + unit;
    }

    private static String formatDuration(Integer seconds) {
        if (seconds == null || seconds <= 0) return null;
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        if (h > 0) return String.format("%dh %02dm", h, m);
        if (m > 0) return String.format("%dm %02ds", m, s);
        return s + "s";
    }
}
