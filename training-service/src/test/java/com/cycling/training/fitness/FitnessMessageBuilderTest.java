package com.cycling.training.fitness;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.cycling.training.form.FormZone;
import com.cycling.training.form.RampAssessment;

import com.cycling.messaging.contract.DeliveryMessage;
import com.cycling.messaging.contract.DeliveryMessage.Field;
import com.cycling.messaging.contract.DeliveryMessage.Section;
import com.cycling.messaging.contract.DeliveryMessage.Target;
import com.cycling.messaging.contract.WellnessUpdated;
import com.cycling.messaging.contract.WellnessUpdated.Values;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FitnessMessageBuilderTest {

    private final FitnessMessageBuilder builder = new FitnessMessageBuilder(List.of(new Target("DISCORD", "fitness")));

    private static WellnessUpdated update(Values today, Values previous) {
        return new WellnessUpdated(today, previous);
    }

    @Test
    void buildsTheDailySummary() {
        DeliveryMessage message = builder.build(update(
                new Values("2026-11-03", 52.3, 61.0, -8.7, 4.1),
                new Values("2026-11-02", 51.5, 55.8, -4.3, 3.9)));

        // fest aus Praefix + Datum abgeleitet: bleibt ueber Neustarts und Versionen gleich (Duplikatschutz)
        assertThat(message.messageId())
                .isEqualTo(UUID.nameUUIDFromBytes("fitness-daily-2026-11-03".getBytes(StandardCharsets.UTF_8)));
        assertThat(message.title()).isEqualTo("📈 Fitness heute");
        assertThat(message.subtitle()).isEqualTo("Dienstag, 03.11.2026");
        assertThat(message.color()).isEqualTo(FormZone.GREY.color());
        assertThat(message.fields()).containsExactly(
                new Field("Fitness (CTL)", "52.3 (+0.8)"),
                new Field("Fatigue (ATL)", "61.0 (+5.2)"),
                new Field("Form (TSB)", "-8.7 (-4.4)"),
                new Field("Ramp", "+4.1 CTL/Woche"));
        assertThat(message.sections()).containsExactly(
                new Section("Form-Zone: ⚫ Grauzone", FormZone.GREY.description(), FormZone.GREY.color()));
        assertThat(message.attachments()).isEmpty();
        assertThat(message.footer()).isEqualTo("Quelle: intervals.icu");
        assertThat(message.targets()).containsExactly(new Target("DISCORD", "fitness"));
    }

    @Test
    void zoneChangeMentionsYesterdaysZone() {
        DeliveryMessage message = builder.build(update(
                new Values("2026-11-03", 50.0, 62.0, -12.0, 2.0),
                new Values("2026-11-02", 49.5, 57.0, -7.5, 2.0)));

        assertThat(message.color()).isEqualTo(FormZone.OPTIMAL.color());
        assertThat(message.sections().getFirst().title()).isEqualTo("Form-Zone: 🟢 Optimal");
        assertThat(message.sections().getFirst().text()).endsWith("(gestern: ⚫ Grauzone)");
    }

    @Test
    void positiveFormHasAPlusSignAndUnchangedValuesShowPlusMinusZero() {
        DeliveryMessage message = builder.build(update(
                new Values("2026-11-03", 50.0, 40.0, 10.0, 0.0),
                new Values("2026-11-02", 50.0, 41.0, 9.0, 0.0)));

        assertThat(message.fields()).contains(
                new Field("Fitness (CTL)", "50.0 (±0.0)"),
                new Field("Form (TSB)", "+10.0 (+1.0)"),
                new Field("Ramp", "0.0 CTL/Woche"));
    }

    @Test
    void rampOutsideTheHealthyRangeAddsAHint() {
        DeliveryMessage message = builder.build(update(new Values("2026-11-03", 60.0, 80.0, -20.0, 9.3), null));

        assertThat(message.sections()).hasSize(2);
        Section ramp = message.sections().get(1);
        assertThat(ramp.title()).isEqualTo(RampAssessment.TOO_FAST.title());
        assertThat(ramp.color()).isEqualTo(RampAssessment.TOO_FAST.color());
        assertThat(ramp.text()).startsWith("Ramp +9.3 CTL/Woche – ");
    }

    @Test
    void withoutPreviousDayValuesHaveNoDelta() {
        DeliveryMessage message = builder.build(update(new Values("2026-11-03", 52.3, 61.0, -8.7, 4.1), null));

        assertThat(message.fields()).containsExactly(
                new Field("Fitness (CTL)", "52.3"),
                new Field("Fatigue (ATL)", "61.0"),
                new Field("Form (TSB)", "-8.7"),
                new Field("Ramp", "+4.1 CTL/Woche"));
        assertThat(message.sections().getFirst().text()).doesNotContain("gestern");
    }

    @Test
    void missingValuesAreLeftOut() {
        DeliveryMessage message = builder.build(update(
                new Values("2026-11-03", 52.3, null, null, null),
                new Values("2026-11-02", null, 55.8, -4.3, 3.9)));

        assertThat(message.fields()).containsExactly(new Field("Fitness (CTL)", "52.3"));
        assertThat(message.sections()).isEmpty();
        assertThat(message.color()).isEqualTo(FitnessMessageBuilder.NO_FORM_COLOR);
    }

    @Test
    void emptyTargetsMeanDefaultDestinations() {
        DeliveryMessage message = new FitnessMessageBuilder(List.of())
                .build(update(new Values("2026-11-03", 52.3, 61.0, -8.7, 4.1), null));

        assertThat(message.targets()).isEmpty();
    }

    @Test
    void rejectsUpdateWithoutToday() {
        assertThatThrownBy(() -> builder.build(update(null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void signedFormatting() {
        assertThat(FitnessMessageBuilder.signed(-0.04)).isEqualTo("0.0");
        assertThat(FitnessMessageBuilder.signed(0.04)).isEqualTo("0.0");
        assertThat(FitnessMessageBuilder.signed(0.05)).isEqualTo("+0.1");
        assertThat(FitnessMessageBuilder.delta(0.0)).isEqualTo("±0.0");
        assertThat(FitnessMessageBuilder.delta(-1.25)).isEqualTo("-1.3");
    }
}
