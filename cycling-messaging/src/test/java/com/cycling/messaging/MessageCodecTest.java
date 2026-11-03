package com.cycling.messaging;

import com.cycling.messaging.contract.ActivityDetected;
import com.cycling.messaging.contract.AnalysisRequest;
import com.cycling.messaging.contract.WellnessUpdated;
import com.cycling.messaging.contract.AnalysisResult;
import com.cycling.messaging.contract.WorkoutData;
import com.cycling.messaging.contract.ZoneShare;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageCodecTest {

    private final MessageCodec codec = new MessageCodec();

    @Test
    void requestSurvivesRoundTrip() {
        WorkoutData ride = new WorkoutData("Runde", "2026-10-30T18:00:00", false, 30.0, 3600,
                200.0, 600.0, 140.0, 175.0, 28.5, 300.0, 88.0, 250, 0.8, null);
        AnalysisRequest request = new AnalysisRequest("job-1", ride,
                List.of(new ZoneShare("Z2", "Ausdauer", 55.5)), List.of(), List.of(ride), 250, 190);

        AnalysisRequest decoded = codec.decode(codec.encode(request), AnalysisRequest.class);

        assertThat(decoded).isEqualTo(request);
    }

    @Test
    void unknownFieldsAreIgnored() {
        byte[] json = """
                {"requestId":"r1","success":true,"score":8,"summary":"gut","newField":42}
                """.getBytes(StandardCharsets.UTF_8);

        AnalysisResult result = codec.decode(json, AnalysisResult.class);

        assertThat(result.requestId()).isEqualTo("r1");
        assertThat(result.score()).isEqualTo(8);
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
        assertThatThrownBy(() -> codec.decode("kein json".getBytes(StandardCharsets.UTF_8), AnalysisResult.class))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
