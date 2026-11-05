package com.cycling.training.fitness;

import com.cycling.training.form.FormZone;
import com.cycling.training.form.RampAssessment;

import com.cycling.messaging.contract.DeliveryMessage;
import com.cycling.messaging.contract.DeliveryMessage.Field;
import com.cycling.messaging.contract.DeliveryMessage.Section;
import com.cycling.messaging.contract.DeliveryMessage.Target;
import com.cycling.messaging.contract.WellnessUpdated;
import com.cycling.messaging.contract.WellnessUpdated.Values;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Macht aus dem Stand von heute (plus Vortag) die Tageszusammenfassung: CTL/ATL/Form mit Delta zum
 * Vortag und Ramp als Felder, die Form-Zone als Abschnitt in Zonenfarbe, dazu ein Ramp-Hinweis, wenn
 * die Ramp ausserhalb des gruenen Bereichs liegt. Die messageId haengt nur am Datum, damit der
 * delivery-service eine zweite Zusammenfassung fuer denselben Tag verwirft.
 */
public class FitnessMessageBuilder {

    static final String MESSAGE_ID_PREFIX = "fitness-daily-";
    static final String NO_FORM_COLOR = "#9e9e9e";
    private static final DateTimeFormatter SUBTITLE_FMT = DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy", Locale.GERMAN);

    private final List<Target> targets;

    /** @param targets Ziele aus der Konfiguration (fitness.targets); leer = Default-Ziele des delivery-service */
    public FitnessMessageBuilder(List<Target> targets) {
        this.targets = List.copyOf(targets);
    }

    /**
     * Fuer jeden Tag immer dieselbe UUID (aus "fitness-daily-" + Datum abgeleitet), damit der delivery-service eine
     * zweite Nachricht fuer denselben Tag als Duplikat erkennt.
     */
    public static UUID messageId(LocalDate date) {
        return UUID.nameUUIDFromBytes((MESSAGE_ID_PREFIX + date).getBytes(StandardCharsets.UTF_8));
    }

    /** @throws IllegalArgumentException wenn {@code update.today()} oder dessen Datum fehlt */
    public DeliveryMessage build(WellnessUpdated update) {
        Values today = update.today();
        if (today == null || today.date() == null) {
            throw new IllegalArgumentException("WellnessUpdated ohne Werte fuer heute");
        }
        Values previous = update.previousDay();
        LocalDate date = LocalDate.parse(today.date());
        FormZone zone = today.form() == null ? null : FormZone.of(today.form());

        return new DeliveryMessage(
                messageId(date),
                "📈 Fitness heute",
                capitalize(date.format(SUBTITLE_FMT)),
                zone == null ? NO_FORM_COLOR : zone.color(),
                fields(today, previous),
                sections(today, previous, zone),
                List.of(),
                "Quelle: intervals.icu",
                targets);
    }

    private static List<Field> fields(Values today, Values previous) {
        List<Field> fields = new ArrayList<>();
        addWithDelta(fields, "Fitness (CTL)", today, previous, Values::ctl, false);
        addWithDelta(fields, "Fatigue (ATL)", today, previous, Values::atl, false);
        addWithDelta(fields, "Form (TSB)", today, previous, Values::form, true);
        if (today.rampRate() != null) {
            fields.add(new Field("Ramp", signed(today.rampRate()) + " CTL/Woche"));
        }
        return fields;
    }

    private static void addWithDelta(List<Field> fields, String name, Values today, Values previous,
                                     Function<Values, Double> value, boolean signedValue) {
        Double now = value.apply(today);
        if (now == null) return;
        String text = signedValue ? signed(now) : plain(now);
        Double before = previous == null ? null : value.apply(previous);
        if (before != null) {
            text += " (" + delta(now - before) + ")";
        }
        fields.add(new Field(name, text));
    }

    private static List<Section> sections(Values today, Values previous, FormZone zone) {
        List<Section> sections = new ArrayList<>();
        if (zone != null) {
            String text = zone.description();
            FormZone yesterday = previous == null || previous.form() == null ? null : FormZone.of(previous.form());
            if (yesterday != null && yesterday != zone) {
                text += " (gestern: " + yesterday.title() + ")";
            }
            sections.add(new Section("Form-Zone: " + zone.title(), text, zone.color()));
        }
        RampAssessment.of(today.rampRate()).ifPresent(ramp -> sections.add(new Section(ramp.title(),
                "Ramp " + signed(today.rampRate()) + " CTL/Woche – " + ramp.advice(), ramp.color())));
        return sections;
    }

    /** Eine Nachkommastelle, Punkt als Trenner, z. B. "52.3". */
    static String plain(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    /** Mit Vorzeichen, z. B. "+3.2", "-8.7", "0.0". */
    static String signed(double value) {
        String text = plain(value);
        if (text.equals("-0.0")) return "0.0";
        return value > 0 && !text.equals("0.0") ? "+" + text : text;
    }

    /** Delta mit Vorzeichen; ohne Aenderung "±0.0". */
    static String delta(double value) {
        String text = signed(value);
        return text.equals("0.0") ? "±0.0" : text;
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
