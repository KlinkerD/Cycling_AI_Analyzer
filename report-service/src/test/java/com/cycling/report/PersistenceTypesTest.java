package com.cycling.report;

import com.cycling.report.analysis.ActivityAnalysis;
import com.cycling.report.analysis.ActivityAnalysisRepository;
import com.cycling.report.analysis.AnalysisJob;
import com.cycling.report.analysis.AnalysisJobRepository;
import com.cycling.report.analysis.AnalysisJobStatus;
import com.cycling.report.analysis.ReportData;
import com.cycling.report.analysis.ReportSnapshotStore;
import com.cycling.report.api.StreamDataDto;
import com.cycling.report.api.ZoneBandDto;
import com.cycling.report.api.ZoneDistributionDto;
import com.cycling.report.ftp.FtpDetection;
import com.cycling.report.ftp.FtpDetectionRepository;
import com.cycling.report.weather.RideWeatherPoint;
import com.cycling.report.weather.RideWeatherService;
import com.cycling.report.weather.WeatherSample;
import com.cycling.types.ActivityId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Alle Repository-Abfragen mit den Werttypen (ActivityId, UUID, Instant) gegen echte H2. Die Unit-Tests mocken die
 * Repositories und sehen Fehler beim Uebersetzen der Abfragen nicht.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:persistence;DB_CLOSE_DELAY=-1",
        "mqtt.broker-url=tcp://localhost:1",
        "mqtt.client-id=persistence-test",
        "weather-service.base-url="
})
class PersistenceTypesTest {

    private static final ActivityId I1 = ActivityId.of("i1");

    @Autowired
    private ActivityAnalysisRepository analyses;
    @Autowired
    private AnalysisJobRepository jobs;
    @Autowired
    private FtpDetectionRepository ftp;
    @Autowired
    private RideWeatherService weather;
    @Autowired
    private ReportSnapshotStore snapshots;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void activityIdIsStoredAsTextAndQueryable() {
        ActivityAnalysis a = new ActivityAnalysis();
        a.setActivityId(I1);
        a.setActivityName("Runde");
        analyses.save(a);

        assertThat(analyses.findAllActivityIds()).contains(I1);
        assertThat(analyses.findByActivityId(I1)).isPresent();
        assertThat(jdbc.queryForObject("SELECT activity_id FROM activity_analysis WHERE activity_id = 'i1'",
                String.class)).isEqualTo("i1");
    }

    @Test
    void jobIdIsUuidStoredAsText() {
        UUID id = UUID.randomUUID();
        jobs.save(new AnalysisJob(id, I1, "{}", LocalDateTime.now()));

        assertThat(jobs.findById(id)).hasValueSatisfying(j -> assertThat(j.getActivityId()).isEqualTo(I1));
        assertThat(jobs.findFirstByActivityIdAndStatusOrderByCreatedAtDesc(I1, AnalysisJobStatus.PENDING)).isPresent();
        assertThat(jdbc.queryForObject("SELECT id FROM analysis_job WHERE id = ?", String.class, id.toString()))
                .isEqualTo(id.toString());
    }

    @Test
    void ftpDetectionKeepsTypedIds() {
        UUID requestId = UUID.randomUUID();
        ftp.save(new FtpDetection(ActivityId.of("i2"), requestId, LocalDateTime.now()));

        assertThat(ftp.findById("i2")).hasValueSatisfying(d -> {
            assertThat(d.getActivityId()).isEqualTo(ActivityId.of("i2"));
            assertThat(d.getRequestId()).isEqualTo(requestId);
        });
    }

    @Test
    void reportSnapshotIsReplacedAndLoaded() {
        StreamDataDto streams = new StreamDataDto(List.of(0.0, 5.5), Arrays.asList(200, null), List.of(140, 141),
                List.of(), List.of(), List.of(0, 1), List.of(), List.of(48.1, 48.2), List.of(11.5, 11.6));
        ReportData first = new ReportData(streams, List.of(), List.of(), List.of(), List.of());
        ReportData second = new ReportData(streams,
                List.of(new ZoneDistributionDto("Z2", "Endurance", 60, 100.0, "#42a5f5")), List.of(),
                List.of(new ZoneBandDto("Z2", 140, null, "#42a5f5")), List.of());

        snapshots.save(ActivityId.of("i4"), first);
        snapshots.save(ActivityId.of("i4"), second);

        assertThat(snapshots.load(ActivityId.of("i4"))).contains(second);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report_snapshot WHERE activity_id = 'i4'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void weatherPointsAreReplacedAndLoaded() {
        Instant start = Instant.parse("2026-11-01T07:30:00Z");
        RideWeatherPoint p = new RideWeatherPoint(new WeatherSample(0, 0.0, 48.1, 11.5, start, 0.0),
                7.0, 12.0, 20.0, 180.0, 0.0);

        weather.replace(ActivityId.of("i3"), List.of(p, p));
        weather.replace(ActivityId.of("i3"), List.of(p));

        assertThat(weather.load(ActivityId.of("i3"))).singleElement()
                .satisfies(w -> assertThat(w.time()).isEqualTo(start));
        assertThat(weather.summary(ActivityId.of("i3"))).isPresent();
    }
}
