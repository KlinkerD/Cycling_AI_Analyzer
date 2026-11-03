package com.cycling.messaging;

import com.cycling.messaging.contract.ActivityDetected;
import com.cycling.messaging.contract.AnalysisRequest;
import com.cycling.messaging.contract.WellnessUpdated;
import com.cycling.messaging.contract.RecommendationRefreshRequest;
import com.cycling.messaging.contract.RecommendationUpdated;
import com.cycling.messaging.contract.AnalysisResult;
import com.cycling.messaging.contract.ReportContent;
import com.cycling.messaging.contract.ReportDeliveryRequest;
import com.cycling.messaging.contract.WorkoutData;
import com.cycling.messaging.contract.ZoneShare;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
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
    void recommendationUpdatedSurvivesRoundTrip() {
        RecommendationUpdated update = new RecommendationUpdated("r1", "TEMPO", "Tempo / Sweet Spot", "#ff9800",
                "Heute Sweet Spot.", 8.0, 4.2, false, LocalDate.of(2026, 11, 3),
                LocalDateTime.of(2026, 11, 3, 9, 0, 5), true, true);

        assertThat(codec.decode(codec.encode(update), RecommendationUpdated.class)).isEqualTo(update);
    }

    @Test
    void recommendationWithoutDataSurvivesRoundTrip() {
        RecommendationUpdated update = new RecommendationUpdated("r2", "INSUFFICIENT_DATA", "Nicht genug Daten",
                "#bdbdbd", "Keine Daten.", null, null, false, null, LocalDateTime.of(2026, 11, 3, 9, 0), false, false);

        assertThat(codec.decode(codec.encode(update), RecommendationUpdated.class)).isEqualTo(update);
    }

    @Test
    void recommendationRefreshSurvivesRoundTrip() {
        RecommendationRefreshRequest request = new RecommendationRefreshRequest("r3");

        assertThat(codec.decode(codec.encode(request), RecommendationRefreshRequest.class)).isEqualTo(request);
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
    void reportDeliveryRequestSurvivesRoundTripWithIsoDates() {
        LocalDateTime date = LocalDateTime.of(2026, 11, 1, 8, 30);
        ReportContent.Analysis analysis = new ReportContent.Analysis("i1", "Runde", date, 42.5, 5400,
                180.0, 195.0, 80.0, 0.78, 140.0, 28.3, 300.0, 88.0, 8,
                "gut", "nichts", "besser", "solide", date.plusHours(2));
        ReportContent.PowerCurve curve = new ReportContent.PowerCurve("i1", "Runde", date,
                List.of(new ReportContent.PowerCurvePoint(5, 900), new ReportContent.PowerCurvePoint(60, null)));
        ReportContent content = new ReportContent(
                analysis,
                new ReportContent.Streams(List.of(0.0, 10.0), Arrays.asList(150, null), List.of(120, 125),
                        List.of(8.0, 8.1), List.of(100.0, 101.0), List.of(0, 1), List.of()),
                List.of(new ReportContent.Zone("Z2", "Ausdauer", 3000, 55.5, "#4caf50")),
                List.of(),
                List.of(new ReportContent.TrendPoint("i1", "Runde", date, 180.0, 140.0, 42.5, 28.3, 8)),
                new ReportContent.PowerCurveSet(curve, List.of(), 250));
        ReportDeliveryRequest request = new ReportDeliveryRequest("r1", "i1", content);

        byte[] json = codec.encode(request);

        assertThat(new String(json, StandardCharsets.UTF_8)).contains("\"activityDate\":\"2026-11-01T08:30:00\"");
        assertThat(codec.decode(json, ReportDeliveryRequest.class)).isEqualTo(request);
    }

    @Test
    void invalidPayloadIsRejected() {
        assertThatThrownBy(() -> codec.decode("kein json".getBytes(StandardCharsets.UTF_8), AnalysisResult.class))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
