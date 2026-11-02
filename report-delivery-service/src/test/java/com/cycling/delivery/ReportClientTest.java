package com.cycling.delivery;

import com.cycling.delivery.report.ReportDto;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Das JSON entspricht dem, was GET /api/activities/{id}/report des analysis-service liefert. */
class ReportClientTest {

    private final ReportClient client = new ReportClient(RestClient.create());

    @Test
    void parsesReportOfAnalysisService() {
        String json = """
                {
                  "analysis": {
                    "id": 7, "activityId": "i1", "activityName": "Morgenrunde",
                    "activityDate": "2026-11-01T08:30:00", "distanceKm": 42.5, "durationSeconds": 5400,
                    "avgPowerWatts": 180.0, "normalizedPowerWatts": 195.0, "intensityFactor": 0.78,
                    "indoor": false, "ftpUsed": 250, "overallScore": 8, "summary": "Solide",
                    "analyzedAt": "2026-11-01T10:00:00.123456",
                    "powerCurve": [{"durationSeconds": 5, "watts": 600}]
                  },
                  "streams": {"distance": [0.0, 100.0], "watts": [150, null], "heartrate": [120, 130],
                              "velocity": [8.0, 8.5], "altitude": [100.0, 101.0], "time": [0, 10], "cadence": []},
                  "powerZones": [{"zoneName": "Z2", "zoneLabel": "Ausdauer", "seconds": 1800, "percentage": 55.5, "color": "#4caf50"}],
                  "hrZones": [],
                  "trend": [{"activityId": "i1", "activityName": "Morgenrunde", "activityDate": "2026-11-01T08:30:00",
                             "avgPowerWatts": 180.0, "overallScore": 8}],
                  "powerCurveSet": {"current": {"activityId": "i1", "activityName": "Morgenrunde",
                                    "activityDate": "2026-11-01T08:30:00", "points": [{"durationSeconds": 5, "watts": 600}]},
                                    "previous": [], "ftpWatts": 250}
                }
                """;

        ReportDto report = client.parse(json);

        assertThat(report.analysis().getActivityName()).isEqualTo("Morgenrunde");
        assertThat(report.analysis().getActivityDate()).isEqualTo(LocalDateTime.of(2026, 11, 1, 8, 30));
        assertThat(report.analysis().getOverallScore()).isEqualTo(8);
        assertThat(report.streams().watts()).containsExactly(150, null);
        assertThat(report.powerZones()).singleElement().satisfies(z -> assertThat(z.percentage()).isEqualTo(55.5));
        assertThat(report.trend()).hasSize(1);
        assertThat(report.powerCurveSet().current().points()).hasSize(1);
        assertThat(report.powerCurveSet().ftpWatts()).isEqualTo(250);
    }

    @Test
    void unreadableAnswerIsRejected() {
        assertThatThrownBy(() -> client.parse("<html>Bad Gateway</html>"))
                .isInstanceOf(IllegalStateException.class);
    }
}
