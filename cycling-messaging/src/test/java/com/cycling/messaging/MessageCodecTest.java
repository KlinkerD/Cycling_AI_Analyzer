package com.cycling.messaging;

import com.cycling.messaging.contract.ActivityDetected;
import com.cycling.messaging.contract.AiRequest;
import com.cycling.messaging.contract.AiResult;
import com.cycling.messaging.contract.WellnessUpdated;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageCodecTest {

    private final MessageCodec codec = new MessageCodec();

    @Test
    void requestSurvivesRoundTrip() {
        AiRequest request = new AiRequest("job-1", "Analysiere: äöü ß 🚴\n- Zeile", 0.3, true);

        AiRequest decoded = codec.decode(codec.encode(request), AiRequest.class);

        assertThat(decoded).isEqualTo(request);
    }

    @Test
    void unknownFieldsAreIgnored() {
        byte[] json = """
                {"requestId":"r1","success":true,"text":"gut","newField":42}
                """.getBytes(StandardCharsets.UTF_8);

        AiResult result = codec.decode(json, AiResult.class);

        assertThat(result.requestId()).isEqualTo("r1");
        assertThat(result.text()).isEqualTo("gut");
    }

    @Test
    void activityDetectedSurvivesRoundTrip() {
        ActivityDetected detected = new ActivityDetected("i1", "Runde", "2026-11-01T08:30:00", "Ride",
                42000.0, 5400, 5600, 180.0, 195.0, 600.0, 140.0, 175.0, 7.8, null);

        assertThat(codec.decode(codec.encode(detected), ActivityDetected.class)).isEqualTo(detected);
    }

    @Test
    void wellnessUpdatedSurvivesRoundTripWithoutPreviousDay() {
        WellnessUpdated updated = new WellnessUpdated(
                new WellnessUpdated.Values("2026-11-03", 55.2, 60.1, -4.9, 3.4), null);

        assertThat(codec.decode(codec.encode(updated), WellnessUpdated.class)).isEqualTo(updated);
    }

    @Test
    void invalidPayloadIsRejected() {
        assertThatThrownBy(() -> codec.decode("kein json".getBytes(StandardCharsets.UTF_8), AiResult.class))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
