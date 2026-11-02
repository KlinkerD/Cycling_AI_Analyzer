package com.cycling.delivery;

import com.cycling.delivery.report.ReportDto;
import com.cycling.delivery.report.ActivityAnalysis;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Postet einen Report per Discord-Bot in einen Kanal, in zwei Nachrichten:
 * 1. Text: Kopf-Embed (Kennzahlen + Score) und je ein Embed pro KI-Text
 * 2. Charts: alle PNGs als Anhaenge
 */
public class DiscordReportSender implements ReportSender {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final ObjectMapper JSON = new ObjectMapper();
    /** Discord-Limit fuer die Beschreibung eines Embeds. */
    static final int MAX_EMBED_DESCRIPTION = 4096;
    /** Discord-Limit fuer alle Embed-Texte einer Nachricht zusammen. */
    static final int MAX_MESSAGE_EMBED_CHARS = 6000;
    /** Teile eines Reports; ReportDelivery.completedParts zaehlt, wie viele davon schon raus sind. */
    private static final int PART_TEXT = 1;
    private static final int PART_CHARTS = 2;

    /** Laengere Rate-Limits nicht abwarten, sondern dem Scheduler-Retry ueberlassen. */
    private static final Duration MAX_RATE_LIMIT_WAIT = Duration.ofSeconds(60);
    private static final Logger log = LoggerFactory.getLogger(DiscordReportSender.class);

    private final DiscordSettings settings;
    private final RestClient restClient;
    private final Sleeper sleeper;

    public DiscordReportSender(DiscordSettings settings, RestClient restClient, Sleeper sleeper) {
        if (settings.enabled()) {
            requireValue(settings.botToken(), "cycling.report.discord.bot-token (DISCORD_BOT_TOKEN)");
            requireValue(settings.channelId(), "cycling.report.discord.channel-id (DISCORD_CHANNEL_ID)");
        }
        this.settings = settings;
        this.restClient = restClient;
        this.sleeper = sleeper;
    }

    @Override
    public DeliveryChannel channel() {
        return DeliveryChannel.DISCORD;
    }

    @Override
    public boolean isEnabled() {
        return settings.enabled();
    }

    @Override
    public void send(ReportDto report, ReportCharts charts, ReportDelivery delivery) throws DeliveryException {
        if (delivery.getCompletedParts() < PART_TEXT) {
            postJson(textMessage(report.analysis()));
            delivery.markPartCompleted();
        }
        if (delivery.getCompletedParts() < PART_CHARTS) {
            if (!charts.all().isEmpty()) {
                postCharts(report.analysis(), charts);
            }
            delivery.markPartCompleted();
        }
    }

    private void postJson(Map<String, Object> payload) throws DeliveryException {
        String json = toJson(payload);
        execute("Text-Nachricht", () -> restClient.post()
                .uri(messagesUrl())
                .header("Authorization", "Bot " + settings.botToken().trim())
                .contentType(MediaType.APPLICATION_JSON)
                .body(json)
                .retrieve()
                .toBodilessEntity());
    }

    private void postCharts(ActivityAnalysis a, ReportCharts charts) throws DeliveryException {
        List<Map<String, Object>> attachments = new ArrayList<>();
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        List<ReportCharts.Chart> all = charts.all();
        for (int i = 0; i < all.size(); i++) {
            ReportCharts.Chart chart = all.get(i);
            String filename = chart.key() + ".png";
            attachments.add(Map.of("id", i, "filename", filename, "description", truncate(chart.title(), 1024)));
            HttpHeaders fileHeaders = new HttpHeaders();
            fileHeaders.setContentType(MediaType.IMAGE_PNG);
            fileHeaders.setContentDisposition(ContentDisposition.formData()
                    .name("files[" + i + "]").filename(filename).build());
            body.add("files[" + i + "]", new HttpEntity<>(chart.png(), fileHeaders));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", truncate("📊 Charts – " + safe(a.getActivityName()), 2000));
        payload.put("attachments", attachments);
        payload.put("allowed_mentions", Map.of("parse", List.of()));
        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        body.add("payload_json", new HttpEntity<>(toJson(payload), jsonHeaders));

        execute("Chart-Nachricht", () -> restClient.post()
                .uri(messagesUrl())
                .header("Authorization", "Bot " + settings.botToken().trim())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .toBodilessEntity());
    }

    /** Fuehrt einen Discord-Aufruf aus und ordnet Fehler als dauerhaft / voruebergehend ein. */
    private void execute(String what, Runnable call) throws DeliveryException {
        try {
            call.run();
        } catch (RestClientResponseException e) {
            Duration wait = e.getStatusCode().value() == 429 ? retryAfter(e) : null;
            if (wait == null) {
                throw classify(what, e);
            }
            // Rate-Limit: einmal die von Discord genannte Zeit warten und erneut versuchen.
            log.info("Discord Rate-Limit fuer {}, warte {} ms.", what, wait.toMillis());
            try {
                sleeper.sleep(wait);
                call.run();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw DeliveryException.temporary("Warten auf Discord-Rate-Limit unterbrochen", ie);
            } catch (RestClientResponseException retryEx) {
                throw classify(what, retryEx);
            } catch (ResourceAccessException retryEx) {
                throw unreachable(what, retryEx);
            }
        } catch (ResourceAccessException e) {
            throw unreachable(what, e);
        }
    }

    private DeliveryException classify(String what, RestClientResponseException e) {
        int status = e.getStatusCode().value();
        String msg = ("Discord " + what + " abgelehnt: HTTP " + status + " " + hint(status)
                + " " + e.getResponseBodyAsString()).trim();
        boolean permanent = e.getStatusCode().is4xxClientError() && status != 429;
        return permanent ? DeliveryException.permanent(msg, e) : DeliveryException.temporary(msg, e);
    }

    private static DeliveryException unreachable(String what, ResourceAccessException e) {
        return DeliveryException.temporary("Discord " + what + " nicht erreichbar: " + e.getMessage(), e);
    }

    /** Wartezeit aus "retry_after" (Sekunden) im Body; null wenn unbekannt oder laenger als erlaubt. */
    private static Duration retryAfter(RestClientResponseException e) {
        try {
            JsonNode node = JSON.readTree(e.getResponseBodyAsString()).path("retry_after");
            if (!node.isNumber()) return null;
            Duration wait = Duration.ofMillis(Math.round(node.asDouble() * 1000));
            return wait.compareTo(MAX_RATE_LIMIT_WAIT) <= 0 ? wait : null;
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private String hint(int status) {
        return switch (status) {
            case 401 -> "(Bot-Token ungueltig)";
            case 403 -> "(Bot fehlen Rechte im Kanal " + settings.channelId() + ")";
            case 404 -> "(Kanal " + settings.channelId() + " existiert nicht oder Bot ist nicht auf dem Server)";
            default -> "";
        };
    }

    private String messagesUrl() {
        return settings.apiBaseUrl() + "/channels/" + settings.channelId().trim() + "/messages";
    }

    // ---------- Nachricht 1: Text-Embeds ----------

    private record AiSection(String title, String text, int color) {
    }

    private Map<String, Object> textMessage(ActivityAnalysis a) {
        Map<String, Object> header = headerEmbed(a);
        List<AiSection> sections = new ArrayList<>();
        addSection(sections, "👍 Was gut lief", a.getStrengths(), 0x4CAF50);
        addSection(sections, "⚠️ Auffälligkeiten", a.getAnomalies(), 0xFF9800);
        addSection(sections, "📈 Fortschritt", a.getProgress(), 0x2196F3);
        addSection(sections, "📝 Fazit", a.getSummary(), 0x3F51B5);

        int used = embedChars(header) + sections.stream().mapToInt(s -> s.title().length()).sum();
        int[] limits = fairShare(sections.stream().mapToInt(s -> s.text().length()).toArray(),
                MAX_MESSAGE_EMBED_CHARS - used);

        List<Map<String, Object>> embeds = new ArrayList<>();
        embeds.add(header);
        for (int i = 0; i < sections.size(); i++) {
            AiSection s = sections.get(i);
            Map<String, Object> embed = new LinkedHashMap<>();
            embed.put("title", s.title());
            embed.put("description", truncate(s.text(), limits[i]));
            embed.put("color", s.color());
            embeds.add(embed);
        }

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("embeds", embeds);
        // KI-Texte duerfen niemanden anpingen (@everyone, @here, Rollen).
        message.put("allowed_mentions", Map.of("parse", List.of()));
        return message;
    }

    private Map<String, Object> headerEmbed(ActivityAnalysis a) {
        List<Map<String, Object>> fields = new ArrayList<>();
        addField(fields, "Score", a.getOverallScore() != null ? a.getOverallScore() + " / 10" : null);
        addField(fields, "Distanz", num(a.getDistanceKm(), 1, "km"));
        addField(fields, "Dauer", formatDuration(a.getDurationSeconds()));
        addField(fields, "Leistung ø", num(a.getAvgPowerWatts(), 0, "W"));
        addField(fields, "NP", num(a.getNormalizedPowerWatts(), 0, "W"));
        addField(fields, "TSS", num(a.getTrainingLoad(), 0, ""));
        addField(fields, "IF", num(a.getIntensityFactor(), 2, ""));
        addField(fields, "Puls ø", num(a.getAvgHeartRate(), 0, "bpm"));
        addField(fields, "Tempo ø", num(a.getAvgSpeedKmh(), 1, "km/h"));
        addField(fields, "Höhenmeter", num(a.getElevationGainM(), 0, "hm"));
        addField(fields, "Kadenz ø", num(a.getAvgCadenceRpm(), 0, "rpm"));

        Map<String, Object> embed = new LinkedHashMap<>();
        embed.put("title", truncate("🚴 " + safe(a.getActivityName()), 256));
        if (a.getActivityDate() != null) {
            embed.put("description", a.getActivityDate().format(DATE_FMT));
        }
        embed.put("color", scoreColor(a.getOverallScore()));
        embed.put("fields", fields);
        return embed;
    }

    private static void addSection(List<AiSection> sections, String title, String text, int color) {
        if (text != null && !text.isBlank()) {
            sections.add(new AiSection(title, text, color));
        }
    }

    /**
     * Verteilt das Zeichenbudget fair: kurze Texte bleiben ganz, das Uebrige teilen sich
     * die langen gleichmaessig. Kein Text wird laenger als das Embed-Limit.
     */
    static int[] fairShare(int[] lengths, int budget) {
        int[] limits = new int[lengths.length];
        Integer[] order = new Integer[lengths.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, java.util.Comparator.comparingInt(i -> lengths[i]));

        int remaining = Math.max(0, budget);
        for (int k = 0; k < order.length; k++) {
            int i = order[k];
            int share = remaining / (order.length - k);
            limits[i] = Math.min(Math.min(lengths[i], share), MAX_EMBED_DESCRIPTION);
            remaining -= limits[i];
        }
        return limits;
    }

    @SuppressWarnings("unchecked")
    private static int embedChars(Map<String, Object> embed) {
        int total = String.valueOf(embed.getOrDefault("title", "")).length()
                + String.valueOf(embed.getOrDefault("description", "")).length();
        for (Map<String, Object> f : (List<Map<String, Object>>) embed.getOrDefault("fields", List.of())) {
            total += f.get("name").toString().length() + f.get("value").toString().length();
        }
        return total;
    }

    private static void addField(List<Map<String, Object>> fields, String name, String value) {
        if (value == null) return;
        fields.add(Map.of("name", name, "value", value, "inline", true));
    }

    // ---------- Helpers ----------

    private static String toJson(Object payload) throws DeliveryException {
        try {
            return JSON.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw DeliveryException.permanent("Discord-Nachricht konnte nicht erzeugt werden: " + e.getMessage(), e);
        }
    }

    private static int scoreColor(Integer score) {
        if (score == null) return 0x9E9E9E;
        if (score >= 8) return 0x4CAF50;
        if (score >= 5) return 0xFF9800;
        return 0xF44336;
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

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static void requireValue(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Discord-Versand aktiv, aber " + key + " fehlt.");
        }
    }
}
