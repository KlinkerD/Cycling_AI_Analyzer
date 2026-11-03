package com.cycling.messaging;

import com.cycling.messaging.contract.DeliveryMessage;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Vertrag zwischen Absendern (report-service, ...) und delivery-service. */
class DeliveryMessageCodecTest {

    private final MessageCodec codec = new MessageCodec();

    private static DeliveryMessage message() {
        return new DeliveryMessage("r1", "🚴 Morgenrunde", "01.11.2026", "#4caf50",
                List.of(new DeliveryMessage.Field("Score", "8 / 10")),
                List.of(new DeliveryMessage.Section("📝 Fazit", "Solide Einheit", "#3f51b5")),
                List.of(new DeliveryMessage.Attachment("speedAltChart", "Geschwindigkeit", new byte[]{(byte) 0x89, 'P', 'N', 'G', 0})),
                "Analysiert am 01.11.2026 10:00 Uhr",
                List.of(new DeliveryMessage.Target("DISCORD", "reports")));
    }

    @Test
    void messageWithBinaryAttachmentSurvivesRoundTrip() {
        DeliveryMessage original = message();

        DeliveryMessage decoded = codec.decode(codec.encode(original), DeliveryMessage.class);

        assertThat(decoded).isEqualTo(original);
        assertThat(decoded.attachments().getFirst().png()).containsExactly((byte) 0x89, 'P', 'N', 'G', 0);
    }

    @Test
    void attachmentIsSentAsBase64() {
        String json = new String(codec.encode(message()), StandardCharsets.UTF_8);

        assertThat(json).contains("\"png\":\"iVBORwA=\"");
    }

    @Test
    void missingListsBecomeEmpty() {
        byte[] json = """
                {"messageId":"m1","title":"Hallo"}
                """.getBytes(StandardCharsets.UTF_8);

        DeliveryMessage decoded = codec.decode(json, DeliveryMessage.class);

        assertThat(decoded.fields()).isEmpty();
        assertThat(decoded.sections()).isEmpty();
        assertThat(decoded.attachments()).isEmpty();
        assertThat(decoded.targets()).isEmpty();
    }
}
