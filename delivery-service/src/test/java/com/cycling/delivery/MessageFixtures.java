package com.cycling.delivery;

import com.cycling.messaging.contract.DeliveryMessage;
import com.cycling.messaging.contract.DeliveryMessage.Attachment;
import com.cycling.messaging.contract.DeliveryMessage.Field;
import com.cycling.messaging.contract.DeliveryMessage.Section;
import com.cycling.messaging.contract.DeliveryMessage.Target;

import java.util.List;

/** Gemeinsame Test-Nachrichten fuer den delivery-service. */
final class MessageFixtures {

    private MessageFixtures() {
    }

    static DeliveryMessage message(String messageId) {
        return new DeliveryMessage(messageId, "🚴 Morgenrunde", "01.11.2026", "#4caf50",
                List.of(new Field("Score", "8 / 10"), new Field("Distanz", "42,5 km")),
                sections("Gleichmaessig getreten", "Puls am Ende hoch", "Mehr Leistung als letzte Woche", "Solide Einheit"),
                List.of(),
                "Analysiert am 01.11.2026 10:00 Uhr",
                List.of());
    }

    static List<Section> sections(String strengths, String anomalies, String progress, String summary) {
        return List.of(
                new Section("👍 Was gut lief", strengths, "#4caf50"),
                new Section("⚠️ Auffälligkeiten", anomalies, "#ff9800"),
                new Section("📈 Fortschritt", progress, "#2196f3"),
                new Section("📝 Fazit", summary, "#3f51b5"));
    }

    static DeliveryMessage withSections(DeliveryMessage m, List<Section> sections) {
        return new DeliveryMessage(m.messageId(), m.title(), m.subtitle(), m.color(), m.fields(), sections,
                m.attachments(), m.footer(), m.targets());
    }

    static DeliveryMessage withAttachments(DeliveryMessage m, Attachment... attachments) {
        return new DeliveryMessage(m.messageId(), m.title(), m.subtitle(), m.color(), m.fields(), m.sections(),
                List.of(attachments), m.footer(), m.targets());
    }

    static DeliveryMessage withTargets(DeliveryMessage m, Target... targets) {
        return new DeliveryMessage(m.messageId(), m.title(), m.subtitle(), m.color(), m.fields(), m.sections(),
                m.attachments(), m.footer(), List.of(targets));
    }

    static Attachment attachment(String name, String title) {
        return new Attachment(name, title, png(name));
    }

    static byte[] png(String marker) {
        return ("PNG-" + marker).getBytes();
    }
}
