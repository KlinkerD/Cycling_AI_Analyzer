package com.cycling.delivery;

import com.cycling.messaging.contract.DeliveryMessage;
import com.cycling.messaging.contract.DeliveryMessage.Attachment;
import com.cycling.messaging.contract.DeliveryMessage.Field;
import com.cycling.messaging.contract.DeliveryMessage.Section;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Versendet eine Nachricht als HTML-Mail an die Adresse des Ziels. Aufbau aus einem allgemeinen
 * Template: Kopf (Titel, Unterzeile, Felder), Abschnitte als Karten, Anhaenge inline (CID), Fusszeile.
 */
public class MailChannelSender implements ChannelSender {

    private static final Logger log = LoggerFactory.getLogger(MailChannelSender.class);
    private static final String DEFAULT_COLOR = "#3f51b5";

    private final MailSettings settings;
    private final JavaMailSender mailSender;
    private final String from;
    /** Zielname -> bereinigte Adresse. */
    private final Map<String, String> recipients = new LinkedHashMap<>();

    public MailChannelSender(MailSettings settings, JavaMailSender mailSender) {
        this.settings = settings;
        this.mailSender = mailSender;
        String configuredFrom = clean(settings.from());
        this.from = configuredFrom.isEmpty() ? clean(settings.fallbackUsername()) : configuredFrom;
        settings.destinations().forEach((name, address) -> recipients.put(name, clean(address)));

        if (settings.enabled()) {
            if (recipients.isEmpty()) {
                throw new IllegalStateException("Mail-Versand aktiv, aber kein Ziel konfiguriert "
                        + "(delivery.mail.destinations.default = REPORT_MAIL_TO oder weitere delivery.mail.destinations.<name>).");
            }
            recipients.forEach((name, address) -> {
                if (!isValidEmail(address)) {
                    throw new IllegalStateException("Mail-Versand aktiv, aber delivery.mail.destinations." + name
                            + " ist keine gueltige Adresse: '" + address + "'.");
                }
            });
            if (!isValidEmail(from)) {
                throw new IllegalStateException(
                        "Mail-Versand aktiv, aber weder delivery.mail.from noch spring.mail.username "
                                + "ist eine gueltige Absenderadresse: '" + from + "'.");
            }
            log.info("Mail-Versand aktiv, Ziele: {}", recipients.keySet());
        }
    }

    @Override
    public DeliveryChannel channel() {
        return DeliveryChannel.MAIL;
    }

    @Override
    public boolean isEnabled() {
        return settings.enabled();
    }

    @Override
    public Set<String> destinations() {
        return recipients.keySet();
    }

    @Override
    public void send(DeliveryMessage message, String destination, Delivery delivery) throws DeliveryException {
        String to = recipients.get(destination);
        if (to == null) {
            throw DeliveryException.permanent("Unbekanntes Mail-Ziel '" + destination + "'", null);
        }
        String subject = (clean(settings.subjectPrefix()) + " " + clean(message.title())
                + (message.subtitle() != null && !message.subtitle().isBlank() ? " (" + clean(message.subtitle()) + ")" : ""))
                .trim();

        try {
            List<String> cids = contentIds(message.attachments());
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());
            try {
                helper.setFrom(from, clean(settings.fromName()));
            } catch (java.io.UnsupportedEncodingException ex) {
                helper.setFrom(from);
            }
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(buildHtml(message, cids), true);
            for (int i = 0; i < message.attachments().size(); i++) {
                helper.addInline(cids.get(i), new ByteArrayResource(message.attachments().get(i).png()), "image/png");
            }

            log.info("Sende Mail von '{}' an '{}' (Ziel {}, Nachricht {})", from, to, destination, message.messageId());
            mailSender.send(mime);
        } catch (MailAuthenticationException | MailParseException | MailPreparationException e) {
            throw DeliveryException.permanent("Mail-Konfiguration/Login fehlerhaft: " + e.getMessage(), e);
        } catch (MailException e) {
            throw DeliveryException.temporary("Mail-Versand fehlgeschlagen: " + e.getMessage(), e);
        } catch (jakarta.mail.MessagingException e) {
            throw DeliveryException.permanent("Mail konnte nicht aufgebaut werden: " + e.getMessage(), e);
        }
    }

    /** Eindeutige CIDs aus den Anhang-Namen (nur sichere Zeichen). */
    private static List<String> contentIds(List<Attachment> attachments) {
        List<String> cids = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (int i = 0; i < attachments.size(); i++) {
            String name = attachments.get(i).name() == null ? "" : attachments.get(i).name().replaceAll("[^A-Za-z0-9_-]", "");
            String cid = name.isEmpty() ? "anhang" + (i + 1) : name;
            if (!used.add(cid)) {
                cid = cid + "-" + (i + 1);
                used.add(cid);
            }
            cids.add(cid);
        }
        return cids;
    }

    /** Entfernt Whitespace, Newlines und das umschliessende '<...>' (kommt manchmal aus ENV-Variablen). */
    static String clean(String s) {
        if (s == null) return "";
        String t = s.trim().replaceAll("[\r\n\t]", "");
        if (t.startsWith("<") && t.endsWith(">") && t.length() >= 2) {
            t = t.substring(1, t.length() - 1).trim();
        }
        return t;
    }

    /** Minimal-Check: genau ein '@', kein Whitespace, mind. ein '.' im Domain-Teil. */
    static boolean isValidEmail(String s) {
        if (s == null) return false;
        int at = s.indexOf('@');
        if (at <= 0 || at != s.lastIndexOf('@') || at == s.length() - 1) return false;
        if (s.chars().anyMatch(Character::isWhitespace)) return false;
        return s.substring(at + 1).contains(".");
    }

    // ---------- HTML-Aufbau ----------

    private static String buildHtml(DeliveryMessage message, List<String> cids) {
        String color = color(message.color(), DEFAULT_COLOR);

        StringBuilder sb = new StringBuilder(8192);
        sb.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>").append(esc(message.title()))
                .append("</title></head>");
        sb.append("<body style=\"font-family:Arial,Helvetica,sans-serif;background:#f5f5f5;margin:0;padding:24px;color:#212121;\">");
        sb.append("<div style=\"max-width:760px;margin:0 auto;\">");

        // Kopf
        sb.append("<div style=\"background:#fff;border-radius:8px;border-top:6px solid ").append(color)
                .append(";padding:24px;box-shadow:0 1px 3px rgba(0,0,0,0.1);margin-bottom:16px;\">");
        sb.append("<h1 style=\"margin:0 0 8px 0;font-size:22px;color:").append(color).append(";\">")
                .append(esc(message.title())).append("</h1>");
        if (message.subtitle() != null && !message.subtitle().isBlank()) {
            sb.append("<div style=\"color:#666;font-size:14px;margin-bottom:16px;\">").append(esc(message.subtitle())).append("</div>");
        }
        if (!message.fields().isEmpty()) {
            sb.append("<div>");
            for (Field f : message.fields()) {
                sb.append(field(f));
            }
            sb.append("</div>");
        }
        sb.append("</div>");

        // Abschnitte
        for (Section s : message.sections()) {
            sb.append(sectionCard(s));
        }

        // Anhaenge
        for (int i = 0; i < message.attachments().size(); i++) {
            sb.append(imageCard(message.attachments().get(i).title(), cids.get(i)));
        }

        if (message.footer() != null && !message.footer().isBlank()) {
            sb.append("<div style=\"text-align:center;color:#888;font-size:12px;margin-top:24px;\">")
                    .append(esc(message.footer())).append("</div>");
        }

        sb.append("</div></body></html>");
        return sb.toString();
    }

    private static String field(Field f) {
        if (f.value() == null || f.value().isBlank()) return "";
        return "<span style=\"display:inline-block;margin:0 20px 10px 0;vertical-align:top;\">"
                + "<span style=\"display:block;font-size:12px;color:#666;\">" + esc(f.name()) + "</span>"
                + "<span style=\"font-size:20px;font-weight:bold;color:#212121;\">" + esc(f.value()) + "</span>"
                + "</span>";
    }

    private static String sectionCard(Section s) {
        if (s.text() == null || s.text().isBlank()) return "";
        String color = color(s.color(), DEFAULT_COLOR);
        return "<div style=\"background:#fff;border-left:4px solid " + color + ";border-radius:6px;"
                + "padding:14px 18px;margin-bottom:12px;box-shadow:0 1px 2px rgba(0,0,0,0.08);\">"
                + (s.title() != null && !s.title().isBlank()
                ? "<div style=\"font-weight:bold;color:" + color + ";margin-bottom:6px;\">" + esc(s.title()) + "</div>" : "")
                + "<div style=\"white-space:pre-wrap;line-height:1.5;color:#333;\">" + esc(s.text()) + "</div>"
                + "</div>";
    }

    private static String imageCard(String title, String cid) {
        return "<div style=\"background:#fff;border-radius:6px;padding:14px;margin-bottom:12px;"
                + "box-shadow:0 1px 2px rgba(0,0,0,0.08);text-align:center;\">"
                + (title != null && !title.isBlank()
                ? "<div style=\"font-weight:bold;text-align:left;margin-bottom:8px;color:#333;\">" + esc(title) + "</div>" : "")
                + "<img src=\"cid:" + cid + "\" alt=\"" + esc(title) + "\" style=\"max-width:100%;height:auto;\" />"
                + "</div>";
    }

    /** Nur "#rrggbb" wird uebernommen, damit nichts ins style-Attribut eingeschleust wird. */
    private static String color(String hex, String fallback) {
        return hex != null && hex.matches("#[0-9a-fA-F]{6}") ? hex : fallback;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
