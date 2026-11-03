package com.cycling.report;

import com.cycling.messaging.contract.DeliveryMessage;
import com.cycling.messaging.contract.DeliveryMessage.Attachment;
import com.cycling.messaging.contract.DeliveryMessage.Field;
import com.cycling.messaging.contract.DeliveryMessage.Section;
import com.cycling.messaging.contract.DeliveryMessage.Target;
import com.cycling.messaging.contract.ReportContent;
import com.cycling.messaging.contract.ReportContent.Analysis;

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

    public DeliveryMessage build(String messageId, ReportContent report, ReportCharts charts) {
        Analysis a = report.analysis();
        return new DeliveryMessage(
                messageId,
                "🚴 " + (a.activityName() == null ? "" : a.activityName()),
                a.activityDate() != null ? a.activityDate().format(DATE_FMT) : null,
                scoreColor(a.overallScore()),
                fields(a),
                sections(a),
                charts.all().stream().map(c -> new Attachment(c.key(), c.title(), c.png())).toList(),
                a.analyzedAt() != null ? "Analysiert am " + a.analyzedAt().format(DATETIME_FMT) + " Uhr" : null,
                targets);
    }

    private static List<Field> fields(Analysis a) {
        List<Field> fields = new ArrayList<>();
        add(fields, "Score", a.overallScore() != null ? a.overallScore() + " / 10" : null);
        add(fields, "Distanz", num(a.distanceKm(), 1, "km"));
        add(fields, "Dauer", formatDuration(a.durationSeconds()));
        add(fields, "Leistung ø", num(a.avgPowerWatts(), 0, "W"));
        add(fields, "NP", num(a.normalizedPowerWatts(), 0, "W"));
        add(fields, "TSS", num(a.trainingLoad(), 0, ""));
        add(fields, "IF", num(a.intensityFactor(), 2, ""));
        add(fields, "Puls ø", num(a.avgHeartRate(), 0, "bpm"));
        add(fields, "Tempo ø", num(a.avgSpeedKmh(), 1, "km/h"));
        add(fields, "Höhenmeter", num(a.elevationGainM(), 0, "hm"));
        add(fields, "Kadenz ø", num(a.avgCadenceRpm(), 0, "rpm"));
        return fields;
    }

    private static List<Section> sections(Analysis a) {
        List<Section> sections = new ArrayList<>();
        addSection(sections, "👍 Was gut lief", a.strengths(), "#4caf50");
        addSection(sections, "⚠️ Auffälligkeiten", a.anomalies(), "#ff9800");
        addSection(sections, "📈 Fortschritt", a.progress(), "#2196f3");
        addSection(sections, "📝 Fazit", a.summary(), "#3f51b5");
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
