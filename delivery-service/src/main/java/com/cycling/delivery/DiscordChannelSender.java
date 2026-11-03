package com.cycling.delivery;

import com.cycling.messaging.contract.DeliveryMessage;
import com.cycling.messaging.contract.DeliveryMessage.Attachment;
import com.cycling.messaging.contract.DeliveryMessage.Field;
import com.cycling.messaging.contract.DeliveryMessage.Section;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Postet eine Nachricht per Discord-Bot in den Channel des Ziels:
 * 1. Text: Kopf-Embed (Titel, Unterzeile, Felder, Fusszeile, Farbe) und je ein Embed pro Abschnitt
 * 2. Anhaenge: alle PNGs, je bis zu 10 pro Nachricht
 */
public class DiscordChannelSender implements ChannelSender {

    private static final ObjectMapper JSON = new ObjectMapper();
    /** Discord-Limit fuer die Beschreibung eines Embeds. */
    static final int MAX_EMBED_DESCRIPTION = 4096;
    /** Discord-Limit fuer alle Embed-Texte einer Nachricht zusammen. */
    static final int MAX_MESSAGE_EMBED_CHARS = 6000;
    /** Discord-Limits pro Nachricht bzw. Embed. */
    private static final int MAX_EMBEDS = 10;
    private static final int MAX_FIELDS = 25;
    private static final int MAX_ATTACHMENTS = 10;
    private static final int DEFAULT_COLOR = 0x9E9E9E;

    /** Laengere Rate-Limits nicht abwarten, sondern dem Scheduler-Retry ueberlassen. */
    private static final Duration MAX_RATE_LIMIT_WAIT = Duration.ofSeconds(60);
    private static final Logger log = LoggerFactory.getLogger(DiscordChannelSender.class);

    private final DiscordSettings settings;
    private final RestClient restClient;
    private final Sleeper sleeper;

    public DiscordChannelSender(DiscordSettings settings, RestClient restClient, Sleeper sleeper) {
        if (settings.enabled()) {
            requireValue(settings.botToken(), "delivery.discord.bot-token (DISCORD_BOT_TOKEN)");
            if (settings.destinations().isEmpty()) {
                throw new IllegalStateException("Discord-Versand aktiv, aber kein Ziel konfiguriert "
                        + "(delivery.discord.destinations.default = DISCORD_CHANNEL_ID oder weitere delivery.discord.destinations.<name>).");
            }
            log.info("Discord-Versand aktiv, Ziele: {}", settings.destinations().keySet());
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
    public Set<String> destinations() {
        return settings.destinations().keySet();
    }

    /** Teil 1 = Text, Teile 2.. = Anhaenge in Paketen zu je 10. */
    @Override
    public void send(DeliveryMessage message, String destination, Delivery delivery) throws DeliveryException {
        String channelId = settings.destinations().get(destination);
        if (channelId == null) {
            throw DeliveryException.permanent("Unbekanntes Discord-Ziel '" + destination + "'", null);
        }
        String url = settings.apiBaseUrl() + "/channels/" + channelId + "/messages";

        if (delivery.getCompletedParts() < 1) {
            postJson(url, channelId, textMessage(message));
            delivery.markPartCompleted();
        }
        List<List<Attachment>> chunks = chunks(message.attachments());
        for (int i = 0; i < chunks.size(); i++) {
            if (delivery.getCompletedParts() < i + 2) {
                postAttachments(url, channelId, message.title(), chunks.get(i), i, chunks.size());
                delivery.markPartCompleted();
            }
        }
    }

    private static List<List<Attachment>> chunks(List<Attachment> attachments) {
        List<List<Attachment>> chunks = new ArrayList<>();
        for (int i = 0; i < attachments.size(); i += MAX_ATTACHMENTS) {
            chunks.add(attachments.subList(i, Math.min(i + MAX_ATTACHMENTS, attachments.size())));
        }
        return chunks;
    }

    private void postJson(String url, String channelId, Map<String, Object> payload) throws DeliveryException {
        String json = toJson(payload);
        execute("Text-Nachricht", channelId, () -> restClient.post()
                .uri(url)
                .header("Authorization", "Bot " + settings.botToken().trim())
                .contentType(MediaType.APPLICATION_JSON)
                .body(json)
                .retrieve()
                .toBodilessEntity());
    }

    private void postAttachments(String url, String channelId, String title, List<Attachment> attachments,
                                 int chunk, int chunkCount) throws DeliveryException {
        List<Map<String, Object>> meta = new ArrayList<>();
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        for (int i = 0; i < attachments.size(); i++) {
            Attachment attachment = attachments.get(i);
            String filename = fileName(attachment.name(), chunk * MAX_ATTACHMENTS + i) + ".png";
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", i);
            entry.put("filename", filename);
            if (attachment.title() != null) entry.put("description", truncate(attachment.title(), 1024));
            meta.add(entry);
            HttpHeaders fileHeaders = new HttpHeaders();
            fileHeaders.setContentType(MediaType.IMAGE_PNG);
            fileHeaders.setContentDisposition(ContentDisposition.formData()
                    .name("files[" + i + "]").filename(filename).build());
            body.add("files[" + i + "]", new HttpEntity<>(attachment.png(), fileHeaders));
        }
        String content = "📎 " + safe(title) + (chunkCount > 1 ? " (" + (chunk + 1) + "/" + chunkCount + ")" : "");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", truncate(content, 2000));
        payload.put("attachments", meta);
        payload.put("allowed_mentions", Map.of("parse", List.of()));
        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        body.add("payload_json", new HttpEntity<>(toJson(payload), jsonHeaders));

        execute("Anhang-Nachricht", channelId, () -> restClient.post()
                .uri(url)
                .header("Authorization", "Bot " + settings.botToken().trim())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .toBodilessEntity());
    }

    /** Nur Buchstaben, Ziffern, '-' und '_' im Dateinamen. */
    private static String fileName(String name, int index) {
        String cleaned = name == null ? "" : name.replaceAll("[^A-Za-z0-9_-]", "");
        return cleaned.isEmpty() ? "anhang" + (index + 1) : cleaned;
    }

    /** Fuehrt einen Discord-Aufruf aus und ordnet Fehler als dauerhaft / voruebergehend ein. */
    private void execute(String what, String channelId, Runnable call) throws DeliveryException {
        try {
            call.run();
        } catch (RestClientResponseException e) {
            Duration wait = e.getStatusCode().value() == 429 ? retryAfter(e) : null;
            if (wait == null) {
                throw classify(what, channelId, e);
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
                throw classify(what, channelId, retryEx);
            } catch (ResourceAccessException retryEx) {
                throw unreachable(what, retryEx);
            }
        } catch (ResourceAccessException e) {
            throw unreachable(what, e);
        }
    }

    private static DeliveryException classify(String what, String channelId, RestClientResponseException e) {
        int status = e.getStatusCode().value();
        String msg = ("Discord " + what + " abgelehnt: HTTP " + status + " " + hint(status, channelId)
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

    private static String hint(int status, String channelId) {
        return switch (status) {
            case 401 -> "(Bot-Token ungueltig)";
            case 403 -> "(Bot fehlen Rechte im Kanal " + channelId + ")";
            case 404 -> "(Kanal " + channelId + " existiert nicht oder Bot ist nicht auf dem Server)";
            default -> "";
        };
    }

    // ---------- Nachricht 1: Text-Embeds ----------

    private Map<String, Object> textMessage(DeliveryMessage message) {
        Map<String, Object> header = headerEmbed(message);
        List<Section> sections = message.sections().stream()
                .filter(s -> s.text() != null && !s.text().isBlank())
                .limit(MAX_EMBEDS - 1)
                .toList();
        if (message.sections().size() > sections.size()) {
            log.warn("Nachricht {}: leere oder ueberzaehlige Abschnitte nicht nach Discord gesendet", message.messageId());
        }

        List<String> titles = sections.stream().map(s -> truncate(safe(s.title()), 256)).toList();
        int used = embedChars(header) + titles.stream().mapToInt(String::length).sum();
        int[] limits = fairShare(sections.stream().mapToInt(s -> s.text().length()).toArray(),
                MAX_MESSAGE_EMBED_CHARS - used);

        List<Map<String, Object>> embeds = new ArrayList<>();
        embeds.add(header);
        for (int i = 0; i < sections.size(); i++) {
            Section s = sections.get(i);
            Map<String, Object> embed = new LinkedHashMap<>();
            if (!titles.get(i).isEmpty()) embed.put("title", titles.get(i));
            embed.put("description", truncate(s.text(), limits[i]));
            embed.put("color", color(s.color()));
            embeds.add(embed);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("embeds", embeds);
        // Texte duerfen niemanden anpingen (@everyone, @here, Rollen).
        payload.put("allowed_mentions", Map.of("parse", List.of()));
        return payload;
    }

    private static Map<String, Object> headerEmbed(DeliveryMessage message) {
        List<Map<String, Object>> fields = new ArrayList<>();
        for (Field f : message.fields().stream().limit(MAX_FIELDS).toList()) {
            if (f.name() == null || f.name().isBlank() || f.value() == null || f.value().isBlank()) continue;
            fields.add(Map.of("name", truncate(f.name(), 256), "value", truncate(f.value(), 1024), "inline", true));
        }

        Map<String, Object> embed = new LinkedHashMap<>();
        embed.put("title", truncate(message.title(), 256));
        if (message.subtitle() != null && !message.subtitle().isBlank()) {
            embed.put("description", truncate(message.subtitle(), 1024));
        }
        embed.put("color", color(message.color()));
        if (!fields.isEmpty()) embed.put("fields", fields);
        if (message.footer() != null && !message.footer().isBlank()) {
            embed.put("footer", Map.of("text", truncate(message.footer(), 2048)));
        }
        return embed;
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
        Object footer = embed.get("footer");
        if (footer instanceof Map<?, ?> f) total += String.valueOf(f.get("text")).length();
        for (Map<String, Object> f : (List<Map<String, Object>>) embed.getOrDefault("fields", List.of())) {
            total += f.get("name").toString().length() + f.get("value").toString().length();
        }
        return total;
    }

    /** "#rrggbb" -> Zahl; alles andere wird grau. */
    static int color(String hex) {
        if (hex == null || !hex.matches("#[0-9a-fA-F]{6}")) return DEFAULT_COLOR;
        return Integer.parseInt(hex.substring(1), 16);
    }

    // ---------- Helpers ----------

    private static String toJson(Object payload) throws DeliveryException {
        try {
            return JSON.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw DeliveryException.permanent("Discord-Nachricht konnte nicht erzeugt werden: " + e.getMessage(), e);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        if (max <= 0) return "";
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
