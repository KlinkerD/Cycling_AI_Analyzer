package com.cycling.training.recommendation;

import java.util.UUID;

import com.cycling.messaging.contract.DeliveryMessage;
import com.cycling.messaging.contract.DeliveryMessage.Field;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecommendationMessageBuilderTest {

    /** Feste UUID pro Name, damit Tests lesbar bleiben. */
    private static UUID uuid(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private final RecommendationMessageBuilder builder = new RecommendationMessageBuilder(List.of());

    private static Recommendation update(Double form, Double ramp, boolean capped, boolean fallback) {
        return new Recommendation(uuid("r1"), "TEMPO", "Tempo / Sweet Spot", "#ff9800", "Heute Sweet Spot.", form,
                ramp, capped, LocalDate.of(2026, 11, 3), LocalDateTime.of(2026, 11, 3, 9, 0), false, fallback);
    }

    @Test
    void buildsMorningMessage() {
        DeliveryMessage message = builder.build(update(8.0, 7.2, true, false), LocalDate.of(2026, 11, 3));

        assertThat(message.messageId()).isEqualTo(UUID.nameUUIDFromBytes("recommendation-daily-2026-11-03".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertThat(message.subtitle()).isEqualTo("Dienstag, 03.11.2026");
        assertThat(message.color()).isEqualTo("#ff9800");
        assertThat(message.fields()).containsExactly(
                new Field("Empfehlung", "Tempo / Sweet Spot"),
                new Field("Form (TSB)", "+8"),
                new Field("Ramp", "+7.2 CTL/Woche – gebremst"));
        assertThat(message.sections().getFirst().text()).isEqualTo("Heute Sweet Spot.");
        assertThat(message.footer()).isEqualTo("Quelle: intervals.icu");
        assertThat(message.targets()).isEmpty();
    }

    @Test
    void withoutDataOnlyCategoryAndFallbackHint() {
        DeliveryMessage message = builder.build(update(null, null, false, true), LocalDate.of(2026, 11, 3));

        assertThat(message.fields()).extracting(Field::name).containsExactly("Empfehlung");
        assertThat(message.footer()).contains("KI-Text nicht verfügbar");
    }

    @Test
    void signedNumbers() {
        assertThat(RecommendationMessageBuilder.signed(-12.0, 0)).isEqualTo("-12");
        assertThat(RecommendationMessageBuilder.signed(-0.04, 1)).isEqualTo("0.0");
        assertThat(RecommendationMessageBuilder.signed(0.0, 0)).isEqualTo("0");
    }
}
