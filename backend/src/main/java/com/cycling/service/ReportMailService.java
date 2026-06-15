package com.cycling.service;

import com.cycling.dto.ReportDto;
import com.cycling.dto.ZoneDistributionDto;
import com.cycling.model.ActivityAnalysis;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Versendet einen HTML-Report per E-Mail. Charts werden via QuickChart als PNG
 * gerendert und inline (CID) eingebettet.
 *
 * Async + fail-soft: Fehler werden nur geloggt und beeinflussen die Analyse nicht.
 */
@Service
public class ReportMailService {

    private static final Logger log = LoggerFactory.getLogger(ReportMailService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final JavaMailSender mailSender;
    private final QuickChartService quickChart;

    @Value("${cycling.report.mail.enabled:false}")
    private boolean enabled;

    @Value("${cycling.report.mail.from:}")
    private String fromRaw;

    /** Fallback, falls cycling.report.mail.from leer ist. */
    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${cycling.report.mail.from-name:Cycling Report}")
    private String fromName;

    @Value("${cycling.report.mail.to:}")
    private String toRaw;

    @Value("${cycling.report.mail.subject-prefix:[Cycling Report]}")
    private String subjectPrefix;

    public ReportMailService(JavaMailSender mailSender, QuickChartService quickChart) {
        this.mailSender = mailSender;
        this.quickChart = quickChart;
    }

    @Async
    public void sendReportEmail(ReportDto report) {
        if (!enabled) {
            log.debug("Mailversand deaktiviert (cycling.report.mail.enabled=false), ueberspringe.");
            return;
        }

        // Werte saeubern (kein Whitespace / Zeilenumbruch in SMTP-Adressen!)
        String to = clean(toRaw);
        String from = clean(fromRaw);
        if (from.isEmpty()) {
            from = clean(mailUsername); // Fallback auf SMTP-Username
        }
        String fromNameClean = clean(fromName);

        if (to.isEmpty()) {
            log.warn("Mailversand aktiv, aber cycling.report.mail.to ist leer - keine Mail gesendet.");
            return;
        }
        if (from.isEmpty()) {
            log.warn("Mailversand aktiv, aber cycling.report.mail.from / spring.mail.username sind leer - keine Mail gesendet.");
            return;
        }
        if (!isValidEmail(from)) {
            log.warn("Ungueltige Absenderadresse '{}' (cycling.report.mail.from) - keine Mail gesendet.", from);
            return;
        }
        if (!isValidEmail(to)) {
            log.warn("Ungueltige Empfaengeradresse '{}' (cycling.report.mail.to) - keine Mail gesendet.", to);
            return;
        }

        ActivityAnalysis a = report.analysis();
        String subject = subjectPrefix + " " + safe(a.getActivityName())
                + (a.getActivityDate() != null ? " (" + a.getActivityDate().format(DATE_FMT) + ")" : "");

        try {
            // Charts rendern (jeweils null falls keine Daten / Fehler)
            byte[] speedAltPng = quickChart.renderSpeedAndAltitude(report.streams());
            byte[] powerHrPng = quickChart.renderPowerAndHr(report.streams());
            byte[] cadencePng = report.streams().cadence() != null && !report.streams().cadence().isEmpty()
                    ? quickChart.renderCadenceOverDistance(report.streams()) : null;
            byte[] powerZonesPng = quickChart.renderZoneDoughnut("Leistungszonen (FTP-basiert)", report.powerZones());
            byte[] hrZonesPng = quickChart.renderZoneDoughnut("Herzfrequenzzonen", report.hrZones());
            byte[] trendPng = quickChart.renderTrend(report.trend());
            byte[] powerCurvePng = quickChart.renderPowerCurve(report.powerCurveSet());

            // CIDs vergeben
            List<InlineImage> images = new ArrayList<>();
            String cidSpeedAlt = addIfPresent(images, "speedAltChart", speedAltPng);
            String cidPowerHr = addIfPresent(images, "powerHrChart", powerHrPng);
            String cidCadence = addIfPresent(images, "cadenceChart", cadencePng);
            String cidPowerZones = addIfPresent(images, "powerZonesChart", powerZonesPng);
            String cidHrZones = addIfPresent(images, "hrZonesChart", hrZonesPng);
            String cidTrend = addIfPresent(images, "trendChart", trendPng);
            String cidPowerCurve = addIfPresent(images, "powerCurveChart", powerCurvePng);

            String html = buildHtml(report, cidSpeedAlt, cidPowerHr, cidCadence, cidPowerZones, cidHrZones, cidTrend, cidPowerCurve);

            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());
            try {
                helper.setFrom(from, fromNameClean);
            } catch (java.io.UnsupportedEncodingException ex) {
                helper.setFrom(from);
            }
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            for (InlineImage img : images) {
                helper.addInline(img.cid, new ByteArrayResource(img.data), "image/png");
            }

            log.info("Sende Report-Mail von '{}' an '{}' (Aktivitaet: {})", from, to, a.getActivityName());
            mailSender.send(mime);
            log.info("Report-Mail an {} versendet ({}).", to, a.getActivityName());
        } catch (Exception e) {
            log.error("Versand der Report-Mail fuer '{}' fehlgeschlagen: {}",
                    a.getActivityName(), e.getMessage(), e);
        }
    }

    /** Entfernt Whitespace, Newlines und das umschliessende '<...>' (kommt manchmal aus ENV-Variablen). */
    private static String clean(String s) {
        if (s == null) return "";
        String t = s.trim().replaceAll("[\\r\\n\\t]", "");
        if (t.startsWith("<") && t.endsWith(">") && t.length() >= 2) {
            t = t.substring(1, t.length() - 1).trim();
        }
        return t;
    }

    private static boolean isValidEmail(String s) {
        if (s == null) return false;
        // Minimal-Check: genau ein '@', kein Whitespace, mind. ein '.' im Domain-Teil
        int at = s.indexOf('@');
        if (at <= 0 || at != s.lastIndexOf('@') || at == s.length() - 1) return false;
        if (s.chars().anyMatch(Character::isWhitespace)) return false;
        return s.substring(at + 1).contains(".");
    }

    private static String addIfPresent(List<InlineImage> list, String cid, byte[] data) {
        if (data == null || data.length == 0) return null;
        list.add(new InlineImage(cid, data));
        return cid;
    }

    // ---------- HTML-Aufbau ----------

    private String buildHtml(ReportDto report,
                             String cidSpeedAlt, String cidPowerHr, String cidCadence,
                             String cidPowerZones, String cidHrZones, String cidTrend,
                             String cidPowerCurve) {
        ActivityAnalysis a = report.analysis();
        String scoreColor = scoreColor(a.getOverallScore());

        StringBuilder sb = new StringBuilder(8192);
        sb.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>Cycling Report</title></head>");
        sb.append("<body style=\"font-family:Arial,Helvetica,sans-serif;background:#f5f5f5;margin:0;padding:24px;color:#212121;\">");
        sb.append("<div style=\"max-width:760px;margin:0 auto;\">");

        // Header
        sb.append("<div style=\"background:#fff;border-radius:8px;padding:24px;box-shadow:0 1px 3px rgba(0,0,0,0.1);margin-bottom:16px;\">");
        sb.append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr>");
        sb.append("<td valign=\"top\">");
        sb.append("<h1 style=\"margin:0 0 8px 0;font-size:22px;color:#3f51b5;\">🚴 ").append(esc(a.getActivityName())).append("</h1>");
        if (a.getActivityDate() != null) {
            sb.append("<div style=\"color:#666;font-size:14px;margin-bottom:16px;\">").append(a.getActivityDate().format(DATE_FMT)).append("</div>");
        }
        sb.append("<div>");
        sb.append(quickStat(num(a.getDistanceKm(), 1), "km"));
        sb.append(quickStat(formatDuration(a.getDurationSeconds()), "Dauer"));
        if (a.getAvgPowerWatts() != null) sb.append(quickStat(num(a.getAvgPowerWatts(), 0), "W ø"));
        if (a.getNormalizedPowerWatts() != null) sb.append(quickStat(num(a.getNormalizedPowerWatts(), 0), "W NP"));
        if (a.getTrainingLoad() != null) sb.append(quickStat(num(a.getTrainingLoad(), 0), "TSS"));
        if (a.getIntensityFactor() != null) sb.append(quickStat(num(a.getIntensityFactor(), 2), "IF"));
        if (a.getAvgHeartRate() != null) sb.append(quickStat(num(a.getAvgHeartRate(), 0), "bpm ø"));
        if (a.getAvgSpeedKmh() != null) sb.append(quickStat(num(a.getAvgSpeedKmh(), 1), "km/h ø"));
        if (a.getElevationGainM() != null) sb.append(quickStat(num(a.getElevationGainM(), 0), "hm"));
        if (a.getAvgCadenceRpm() != null) sb.append(quickStat(num(a.getAvgCadenceRpm(), 0), "rpm ø"));
        sb.append("</div>");
        sb.append("</td>");
        // Score-Kreis
        sb.append("<td valign=\"top\" align=\"right\" width=\"140\">");
        sb.append("<div style=\"width:110px;height:110px;border-radius:50%;border:6px solid ").append(scoreColor)
                .append(";display:inline-block;text-align:center;line-height:1;padding-top:28px;box-sizing:border-box;\">");
        sb.append("<div style=\"font-size:34px;font-weight:bold;color:").append(scoreColor).append(";\">")
                .append(a.getOverallScore() != null ? a.getOverallScore() : "?").append("</div>");
        sb.append("<div style=\"font-size:12px;color:#666;margin-top:4px;\">/ 10</div>");
        sb.append("</div>");
        sb.append("</td></tr></table>");
        sb.append("</div>");

        // KI-Analyse-Karten
        sb.append("<div style=\"display:block;\">");
        sb.append(aiCard("👍 Was gut lief", a.getStrengths(), "#4caf50"));
        sb.append(aiCard("⚠️ Auffälligkeiten", a.getAnomalies(), "#ff9800"));
        sb.append(aiCard("📈 Fortschritt", a.getProgress(), "#2196f3"));
        sb.append(aiCard("📝 Fazit", a.getSummary(), "#3f51b5"));
        sb.append("</div>");

        // Charts
        sb.append("<h2 style=\"font-size:18px;color:#3f51b5;margin:24px 0 12px;\">Leistungsanalyse</h2>");
        sb.append(chartCard("Geschwindigkeit & Höhe über Distanz", cidSpeedAlt));
        sb.append(chartCard("Leistung & Herzfrequenz über Distanz", cidPowerHr));
        sb.append(chartCard("Kadenz über Distanz", cidCadence));
        sb.append(chartCard("Leistungszonen (FTP-basiert)", cidPowerZones));
        sb.append(chartCard("Herzfrequenzzonen", cidHrZones));

        // Zonen-Tabellen als Fallback / zusaetzliche Info
        sb.append(zoneTable("Leistungszonen", report.powerZones()));
        sb.append(zoneTable("Herzfrequenzzonen", report.hrZones()));

        // Bestleistungs-Kurve (Power Curve)
        if (cidPowerCurve != null) {
            sb.append("<h2 style=\"font-size:18px;color:#3f51b5;margin:24px 0 12px;\">Bestleistungen</h2>");
            String prevCount = report.powerCurveSet() != null && report.powerCurveSet().previous() != null
                    ? String.valueOf(report.powerCurveSet().previous().size()) : "0";
            sb.append(chartCard("Aktuelles Training vs. " + prevCount + " vorherige (transparent)", cidPowerCurve));
        }

        // Trend
        if (cidTrend != null) {
            sb.append("<h2 style=\"font-size:18px;color:#3f51b5;margin:24px 0 12px;\">Trainingsfortschritt</h2>");
            sb.append(chartCard("Letzte " + report.trend().size() + " analysierte Trainings", cidTrend));
        }

        // Footer
        sb.append("<div style=\"text-align:center;color:#888;font-size:12px;margin-top:24px;\">Analysiert am ");
        sb.append(a.getAnalyzedAt() != null ? a.getAnalyzedAt().format(DATETIME_FMT) : "–").append(" Uhr</div>");

        sb.append("</div></body></html>");
        return sb.toString();
    }

    private static String quickStat(String value, String unit) {
        return "<span style=\"display:inline-block;margin:0 16px 8px 0;\">"
                + "<span style=\"font-size:20px;font-weight:bold;color:#212121;\">" + esc(value) + "</span>"
                + " <span style=\"font-size:12px;color:#666;\">" + esc(unit) + "</span>"
                + "</span>";
    }

    private static String aiCard(String title, String text, String color) {
        if (text == null || text.isBlank()) return "";
        return "<div style=\"background:#fff;border-left:4px solid " + color + ";border-radius:6px;"
                + "padding:14px 18px;margin-bottom:12px;box-shadow:0 1px 2px rgba(0,0,0,0.08);\">"
                + "<div style=\"font-weight:bold;color:" + color + ";margin-bottom:6px;\">" + esc(title) + "</div>"
                + "<div style=\"white-space:pre-wrap;line-height:1.5;color:#333;\">" + esc(text) + "</div>"
                + "</div>";
    }

    private static String chartCard(String title, String cid) {
        if (cid == null) return "";
        return "<div style=\"background:#fff;border-radius:6px;padding:14px;margin-bottom:12px;"
                + "box-shadow:0 1px 2px rgba(0,0,0,0.08);text-align:center;\">"
                + "<div style=\"font-weight:bold;text-align:left;margin-bottom:8px;color:#333;\">" + esc(title) + "</div>"
                + "<img src=\"cid:" + cid + "\" alt=\"" + esc(title) + "\" style=\"max-width:100%;height:auto;\" />"
                + "</div>";
    }

    private static String zoneTable(String title, List<ZoneDistributionDto> zones) {
        if (zones == null || zones.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"background:#fff;border-radius:6px;padding:14px 18px;margin-bottom:12px;box-shadow:0 1px 2px rgba(0,0,0,0.08);\">");
        sb.append("<div style=\"font-weight:bold;margin-bottom:8px;color:#333;\">").append(esc(title)).append("</div>");
        sb.append("<table width=\"100%\" cellpadding=\"4\" cellspacing=\"0\" style=\"border-collapse:collapse;font-size:13px;\">");
        for (ZoneDistributionDto z : zones) {
            sb.append("<tr>");
            sb.append("<td style=\"width:14px;\"><div style=\"width:12px;height:12px;background:")
                    .append(esc(z.color())).append(";border-radius:2px;\"></div></td>");
            sb.append("<td><b>").append(esc(z.zoneName())).append("</b> ").append(esc(z.zoneLabel())).append("</td>");
            sb.append("<td align=\"right\">").append(formatDuration(z.seconds())).append("</td>");
            sb.append("<td align=\"right\" style=\"width:60px;\">").append(String.format(Locale.GERMAN, "%.1f", z.percentage())).append(" %</td>");
            sb.append("</tr>");
        }
        sb.append("</table></div>");
        return sb.toString();
    }

    // ---------- Helpers ----------

    private static String formatDuration(Integer seconds) {
        if (seconds == null || seconds <= 0) return "–";
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        if (h > 0) return String.format("%dh %02dm", h, m);
        if (m > 0) return String.format("%dm %02ds", m, s);
        return s + "s";
    }

    private static String num(Double v, int digits) {
        if (v == null) return "–";
        return String.format(Locale.GERMAN, "%." + digits + "f", v);
    }

    private static String scoreColor(Integer score) {
        if (score == null) return "#9e9e9e";
        if (score >= 8) return "#4caf50";
        if (score >= 5) return "#ff9800";
        return "#f44336";
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private record InlineImage(String cid, byte[] data) {}
}

