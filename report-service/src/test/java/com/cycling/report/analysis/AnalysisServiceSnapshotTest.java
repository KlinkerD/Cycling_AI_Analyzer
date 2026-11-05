package com.cycling.report.analysis;

import com.cycling.intervals.client.IntervalsUnavailableException;
import com.cycling.report.api.ActivityDto;
import com.cycling.report.api.ReportDto;
import com.cycling.report.api.StreamDataDto;
import com.cycling.report.api.ZoneBandDto;
import com.cycling.report.api.ZoneDistributionDto;
import com.cycling.report.weather.RideWeatherService;
import com.cycling.report.weather.WeatherPointRepository;
import com.cycling.types.ActivityId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Reports kommen aus den gespeicherten Daten; "Daten neu laden" holt sie frisch. */
class AnalysisServiceSnapshotTest {

    private static final ActivityId I1 = ActivityId.of("i1");

    private static final StreamDataDto STREAMS = new StreamDataDto(List.of(0.0, 5.0), List.of(200, 210),
            List.of(140, 141), List.of(), List.of(), List.of(0, 1), List.of(), List.of(), List.of());
    private static final List<ZoneDistributionDto> POWER_ZONES =
            List.of(new ZoneDistributionDto("Z2", "Endurance", 60, 100.0, "#42a5f5"));
    private static final List<ZoneBandDto> POWER_BANDS = List.of(new ZoneBandDto("Z2", 140, 190, "#42a5f5"));
    private static final ReportData STORED = new ReportData(STREAMS, POWER_ZONES, List.of(), POWER_BANDS, List.of());

    private final IntervalsData intervals = mock(IntervalsData.class);
    private final ActivityAnalysisRepository repository = mock(ActivityAnalysisRepository.class);
    private final PowerCurveService powerCurves = mock(PowerCurveService.class);
    private final ReportSnapshotStore snapshots = mock(ReportSnapshotStore.class);
    private final WeatherPointRepository weatherPoints = mock(WeatherPointRepository.class);
    private AnalysisService service;
    private ActivityAnalysis analysis;

    @BeforeEach
    void setUp() {
        service = new AnalysisService(intervals, mock(ZoneCalculatorService.class), repository, powerCurves,
                new FtpResolver(200), new RideWeatherService(null, weatherPoints), snapshots);

        analysis = new ActivityAnalysis();
        analysis.setActivityId(I1);
        analysis.setActivityDate(LocalDateTime.of(2026, 11, 1, 9, 0));
        analysis.setIndoor(false);
        analysis.setFtpUsed(250);
        when(repository.findByActivityId(I1)).thenReturn(Optional.of(analysis));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void reportWithStoredDataNeedsNoIntervalsService() {
        when(snapshots.load(I1)).thenReturn(Optional.of(STORED));

        ReportDto report = service.getReport(I1);

        assertThat(report.streams()).isEqualTo(STREAMS);
        assertThat(report.powerZones()).isEqualTo(POWER_ZONES);
        assertThat(report.powerZoneBands()).isEqualTo(POWER_BANDS);
        verifyNoInteractions(intervals);
        verify(snapshots, never()).save(any(), any());
    }

    @Test
    void reportWithoutStoredDataLoadsOnceAndStores() {
        liveData();

        service.getReport(I1);

        ReportData saved = savedSnapshot();
        assertThat(saved.streams()).isEqualTo(STREAMS);
        assertThat(saved.powerBands()).isEqualTo(POWER_BANDS);
        verify(intervals).getZoneBandsOrThrow(250);
    }

    @Test
    void refreshStoresFreshDataAndPowerCurve() {
        when(snapshots.load(I1)).thenReturn(Optional.of(STORED));
        liveData();
        PowerCurvePoint point = new PowerCurvePoint();
        when(powerCurves.buildPointsForActivity(I1)).thenReturn(List.of(point));

        ReportDto report = service.refresh(I1);

        assertThat(savedSnapshot().streams()).isEqualTo(STREAMS);
        assertThat(analysis.getPowerCurve()).containsExactly(point);
        verify(repository).save(analysis);
        assertThat(report.streams()).isEqualTo(STREAMS);
    }

    @Test
    void refreshKeepsStoredWeatherWhenNoNewWeatherArrives() {
        liveData();

        service.refresh(I1);

        // Wetter abgeschaltet -> keine Punkte -> gespeichertes Wetter bleibt
        verify(weatherPoints, never()).deleteByActivityId(any());
    }

    @Test
    void refreshFillsTypeAndStartOfOldAnalyses() {
        liveData();
        ActivityDto activity = new ActivityDto();
        activity.setId(I1);
        activity.setType("Ride");
        activity.setStartDate(Instant.parse("2026-11-01T08:00:00Z"));
        when(intervals.findActivity(eq(I1), eq(LocalDate.of(2026, 11, 1)))).thenReturn(Optional.of(activity));

        service.refresh(I1);

        assertThat(analysis.getActivityType()).isEqualTo("Ride");
        assertThat(analysis.getStartDate()).isEqualTo(Instant.parse("2026-11-01T08:00:00Z"));
    }

    @Test
    void failedRefreshKeepsStoredData() {
        when(intervals.getStreams(I1)).thenThrow(new IntervalsUnavailableException("intervals-service weg", null));

        assertThatThrownBy(() -> service.refresh(I1)).isInstanceOf(IntervalsUnavailableException.class);

        verify(snapshots, never()).save(any(), any());
        verify(weatherPoints, never()).deleteByActivityId(any());
    }

    @Test
    void newAnalysisStoresItsData() {
        when(intervals.getAthleteThresholds()).thenReturn(new AthleteThresholdsDto("A", 250, null, null, null, null));
        when(intervals.getStreams(I1)).thenReturn(STREAMS);
        when(intervals.getActivityZones(I1)).thenReturn(new ActivityZonesDto(POWER_ZONES, List.of(), false, null));
        when(intervals.getZoneBands(any())).thenReturn(new IntervalsData.ZoneBands(POWER_BANDS, List.of()));
        when(repository.findByActivityId(I1)).thenReturn(Optional.empty());
        ActivityDto activity = new ActivityDto();
        activity.setId(I1);
        activity.setType("Ride");
        activity.setMovingTime(3600);

        service.complete(service.prepare(I1, activity), new AiAssessment(7, "s", "a", "p", "sum"));

        ReportData saved = savedSnapshot();
        assertThat(saved.streams()).isEqualTo(STREAMS);
        assertThat(saved.powerZones()).isEqualTo(POWER_ZONES);
        assertThat(saved.powerBands()).isEqualTo(POWER_BANDS);
    }

    private void liveData() {
        when(intervals.getStreams(I1)).thenReturn(STREAMS);
        when(intervals.getActivityZonesOrThrow(I1)).thenReturn(new ActivityZonesDto(POWER_ZONES, List.of(), false, 250));
        when(intervals.getZoneBandsOrThrow(any())).thenReturn(new IntervalsData.ZoneBands(POWER_BANDS, List.of()));
        when(powerCurves.buildPointsForActivity(I1)).thenReturn(List.of());
    }

    private ReportData savedSnapshot() {
        ArgumentCaptor<ReportData> captor = ArgumentCaptor.forClass(ReportData.class);
        verify(snapshots).save(eq(I1), captor.capture());
        return captor.getValue();
    }
}
