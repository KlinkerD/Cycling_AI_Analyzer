package com.cycling.messaging;

import com.cycling.messaging.contract.ActivityDetected;
import com.cycling.messaging.contract.AiRequest;
import com.cycling.messaging.contract.AiResult;
import com.cycling.messaging.contract.DetectFtpRequest;
import com.cycling.messaging.contract.DetectFtpResponse;
import com.cycling.messaging.contract.WellnessUpdated;
import org.junit.jupiter.api.Test;

import com.cycling.types.ActivityId;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageCodecTest {

    private static final UUID R1 = UUID.fromString("6f1c2a4e-0d5b-4c1e-9a7f-2b3c4d5e6f70");

    private final MessageCodec codec = new MessageCodec();

    @Test
    void requestSurvivesRoundTrip() {
        AiRequest request = new AiRequest(R1, "Analysiere: äöü ß 🚴\n- Zeile", 0.3, true);

        AiRequest decoded = codec.decode(codec.encode(request), AiRequest.class);

        assertThat(decoded).isEqualTo(request);
    }

    @Test
    void unknownFieldsAreIgnored() {
        byte[] json = """
                {"requestId":"6f1c2a4e-0d5b-4c1e-9a7f-2b3c4d5e6f70","success":true,"text":"gut","newField":42}
                """.getBytes(StandardCharsets.UTF_8);

        AiResult result = codec.decode(json, AiResult.class);

        assertThat(result.requestId()).isEqualTo(R1);
        assertThat(result.text()).isEqualTo("gut");
    }

    @Test
    void activityDetectedSurvivesRoundTrip() {
        ActivityDetected detected = new ActivityDetected(ActivityId.of("i1"), "Runde",
                LocalDateTime.parse("2026-11-01T08:30:00"), "Ride",
                42000.0, 5400, 5600, 180.0, 195.0, 600.0, 140.0, 175.0, 7.8, null,
                Instant.parse("2026-11-01T07:30:00Z"));

        byte[] json = codec.encode(detected);

        // auf der Leitung weiter einfache Strings wie vorher
        assertThat(new String(json, StandardCharsets.UTF_8))
                .contains("\"activityId\":\"i1\"")
                .contains("\"startDateLocal\":\"2026-11-01T08:30:00\"")
                .contains("\"startDate\":\"2026-11-01T07:30:00Z\"");
        assertThat(codec.decode(json, ActivityDetected.class)).isEqualTo(detected);
    }

    @Test
    void wellnessUpdatedSurvivesRoundTripWithoutPreviousDay() {
        WellnessUpdated updated = new WellnessUpdated(
                new WellnessUpdated.Values("2026-11-03", 55.2, 60.1, -4.9, 3.4), null);

        assertThat(codec.decode(codec.encode(updated), WellnessUpdated.class)).isEqualTo(updated);
    }

    @Test
    void detectFtpResponseSurvivesRoundTripAndKeepsFieldNames() {
        DetectFtpResponse response = new DetectFtpResponse(R1, ActivityId.of("i1"), true, 266, 182, 171, 280, 900, null);

        byte[] json = codec.encode(response);

        assertThat(new String(json, StandardCharsets.UTF_8)).contains("\"isFtpDetected\":true");
        assertThat(codec.decode(json, DetectFtpResponse.class)).isEqualTo(response);
    }

    @Test
    void detectFtpRequestSurvivesRoundTrip() {
        DetectFtpRequest request = new DetectFtpRequest(R1, ActivityId.of("i1"));

        assertThat(codec.decode(codec.encode(request), DetectFtpRequest.class)).isEqualTo(request);
    }

    @Test
    void invalidPayloadIsRejected() {
        assertThatThrownBy(() -> codec.decode("kein json".getBytes(StandardCharsets.UTF_8), AiResult.class))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
