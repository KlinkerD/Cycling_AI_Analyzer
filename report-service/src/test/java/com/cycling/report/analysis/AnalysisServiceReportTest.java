package com.cycling.report.analysis;

import com.cycling.types.ActivityId;

import com.cycling.report.weather.RideWeatherService;
import com.cycling.report.weather.WeatherPointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Nachtragen von Indoor-Kennung und FTP beim Oeffnen gespeicherter Reports ohne gespeicherte Daten. */
class AnalysisServiceReportTest {

    private final IntervalsData intervals = mock(IntervalsData.class);
    private final ActivityAnalysisRepository repository = mock(ActivityAnalysisRepository.class);
    private final PowerCurveService powerCurves = mock(PowerCurveService.class);
    private final ReportSnapshotStore snapshots = mock(ReportSnapshotStore.class);
    private AnalysisService service;

    @BeforeEach
    void setUp() {
        service = new AnalysisService(
                intervals,
                mock(ZoneCalculatorService.class),
                repository,
                powerCurves,
                new FtpResolver(200),
                new RideWeatherService(null, mock(WeatherPointRepository.class)),
                snapshots);
        when(intervals.getAthleteThresholds())
                .thenReturn(new AthleteThresholdsDto("Test", 250, 235, null, null, null));
        when(intervals.getZoneBandsOrThrow(any())).thenReturn(IntervalsData.ZoneBands.empty());
    }

    /** Analyse aus alter Version: NP 200 W, 1 h, mit Outdoor-FTP 250 W gerechnet, ohne indoor/ftpUsed. */
    private ActivityAnalysis storedOldAnalysis() {
        ActivityAnalysis a = new ActivityAnalysis();
        a.setActivityId(ActivityId.of("i1"));
        a.setNormalizedPowerWatts(200.0);
        a.setDurationSeconds(3600);
        a.setIntensityFactor(0.80);
        a.setTrainingLoad(64.0);
        when(repository.findByActivityId(ActivityId.of("i1"))).thenReturn(Optional.of(a));
        return a;
    }

    private void activityFromIntervals(Boolean indoor, Integer icuFtp) {
        when(intervals.getActivityZonesOrThrow(ActivityId.of("i1")))
                .thenReturn(new ActivityZonesDto(List.of(), List.of(), indoor, icuFtp));
    }

    @Test
    void oldIndoorAnalysisWithoutIcuFtpGetsIndoorFtpAndIsSaved() {
        ActivityAnalysis a = storedOldAnalysis();
        activityFromIntervals(true, null);

        service.getReport(ActivityId.of("i1"));

        assertThat(a.getIndoor()).isTrue();
        assertThat(a.getFtpUsed()).isEqualTo(235);
        assertThat(a.getTrainingLoad()).isEqualTo(72.0);
        verify(repository).save(a);
        verify(powerCurves).buildReportSet(eq(a), anyList(), eq(235));
        // Kartenzonen mit derselben FTP wie IF/TSS
        verify(intervals).getZoneBandsOrThrow(235);
    }

    @Test
    void oldAnalysisPrefersIcuFtpOfActivity() {
        ActivityAnalysis a = storedOldAnalysis();
        activityFromIntervals(true, 240);

        service.getReport(ActivityId.of("i1"));

        assertThat(a.getFtpUsed()).isEqualTo(240);
    }

    @Test
    void analysisWithStoredFtpIsNotTouched() {
        ActivityAnalysis a = storedOldAnalysis();
        a.setIndoor(false);
        a.setFtpUsed(250);
        activityFromIntervals(true, 235);

        service.getReport(ActivityId.of("i1"));

        assertThat(a.getIndoor()).isFalse();
        assertThat(a.getFtpUsed()).isEqualTo(250);
        assertThat(a.getTrainingLoad()).isEqualTo(64.0);
        verify(repository, never()).save(any());
        verify(powerCurves).buildReportSet(eq(a), anyList(), eq(250));
    }

    @Test
    void unknownIndoorStatusCountsAsOutdoor() {
        ActivityAnalysis a = storedOldAnalysis();
        activityFromIntervals(null, null);

        service.getReport(ActivityId.of("i1"));

        assertThat(a.getIndoor()).isFalse();
        assertThat(a.getFtpUsed()).isEqualTo(250);
    }
}
