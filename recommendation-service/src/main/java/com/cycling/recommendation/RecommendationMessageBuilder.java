package com.cycling.recommendation;

import com.cycling.messaging.contract.DeliveryMessage;
import com.cycling.messaging.contract.DeliveryMessage.Field;
import com.cycling.messaging.contract.DeliveryMessage.Section;
import com.cycling.messaging.contract.DeliveryMessage.Target;
import com.cycling.messaging.contract.RecommendationUpdated;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Macht aus der Empfehlung die Morgen-Nachricht: Kategorie in ihrer Farbe, Form und Ramp als Felder,
 * der (KI- oder Fallback-)Text als Abschnitt. Die messageId haengt nur am Datum, damit der
 * delivery-service eine zweite Nachricht fuer denselben Tag verwirft.
 */
public class RecommendationMessageBuilder {

    static final String MESSAGE_ID_PREFIX = "recommendation-daily-";
    private static final DateTimeFormatter SUBTITLE_FMT = DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy", Locale.GERMAN);

    private final List<Target> targets;

    /** @param targets Ziele aus der Konfiguration (recommendation.targets); leer = Default-Ziele des delivery-service */
    public RecommendationMessageBuilder(List<Target> targets) {
        this.targets = List.copyOf(targets);
    }

    public static String messageId(LocalDate date) {
        return MESSAGE_ID_PREFIX + date;
    }

    /** @param date Versandtag (bestimmt messageId und Unterzeile) */
    public DeliveryMessage build(RecommendationUpdated update, LocalDate date) {
        return new DeliveryMessage(
                messageId(date),
                "🚴 Empfehlung für heute",
                capitalize(date.format(SUBTITLE_FMT)),
                update.color(),
                fields(update),
                List.of(new Section(update.categoryLabel(), update.text(), update.color())),
                List.of(),
                update.fallbackUsed() ? "Quelle: intervals.icu · KI-Text nicht verfügbar" : "Quelle: intervals.icu",
                targets);
    }

    private static List<Field> fields(RecommendationUpdated update) {
        List<Field> fields = new ArrayList<>();
        fields.add(new Field("Empfehlung", update.categoryLabel()));
        if (update.form() != null) {
            fields.add(new Field("Form (TSB)", signed(update.form(), 0)));
        }
        if (update.rampRate() != null) {
            String ramp = signed(update.rampRate(), 1) + " CTL/Woche";
            fields.add(new Field("Ramp", update.rampCapped() ? ramp + " – gebremst" : ramp));
        }
        return fields;
    }

    /** Mit Vorzeichen, z. B. "+8", "-12", "+4.2", "0". */
    static String signed(double value, int digits) {
        String text = String.format(Locale.ROOT, "%." + digits + "f", value);
        if (text.matches("-0(\\.0+)?")) text = text.substring(1);
        return value > 0 && !text.matches("0(\\.0+)?") ? "+" + text : text;
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
