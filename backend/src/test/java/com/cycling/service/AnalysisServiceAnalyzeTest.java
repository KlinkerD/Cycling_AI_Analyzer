package com.cycling.service;

import com.cycling.dto.ActivityDto;
import com.cycling.dto.ActivityZonesDto;
import com.cycling.dto.AthleteThresholdsDto;
import com.cycling.dto.StreamDataDto;
import com.cycling.model.ActivityAnalysis;
import com.cycling.repository.ActivityAnalysisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Neue Analysen speichern Indoor-Kennung und die fuer die Fahrt gueltige FTP. */
class AnalysisServiceAnalyzeTest {

    private final IntervalsService intervals = mock(IntervalsService.class);
    private final GeminiService gemini = mock(GeminiService.class);
    private final ActivityAnalysisRepository repository = mock(ActivityAnalysisRepository.class);
    private final PowerCurveService powerCurves = mock(PowerCurveService.class);
    private AnalysisService service;

    @BeforeEach
    void setUp() {
        service = new AnalysisService(
                intervals, gemini, mock(ZoneCalculatorService.class), repository,
                mock(ReportMailService.class), powerCurves, new FtpResolver(200));

        when(intervals.getAthleteThresholds())
                .thenReturn(new AthleteThresholdsDto("Test", 250, 235, null, null, null));
        when(intervals.getStreams("i1"))
                .thenReturn(new StreamDataDto(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()));
        when(gemini.analyzeWorkout(any(), anyList(), anyList(), anyList(), anyInt(), anyInt()))
                .thenReturn(new GeminiService.AnalysisResult(7, "s", "a", "p", "sum"));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    /** 1 h mit NP 200 W. */
    private static ActivityDto activity(String type) {
        ActivityDto dto = new ActivityDto();
        dto.setId("i1");
        dto.setName("Test-Fahrt");
        dto.setType(type);
        dto.setMovingTime(3600);
        dto.setIcuWeightedAvgWatts(200.0);
        return dto;
    }

    private ActivityAnalysis savedAnalysis() {
        ArgumentCaptor<ActivityAnalysis> captor = ArgumentCaptor.forClass(ActivityAnalysis.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void indoorRideUsesIndoorFtpEverywhere() {
        when(intervals.getActivityZones("i1")).thenReturn(new ActivityZonesDto(List.of(), List.of(), true, null));

        service.analyzeActivity("i1", activity("VirtualRide"));

        ActivityAnalysis saved = savedAnalysis();
        assertThat(saved.getIndoor()).isTrue();
        assertThat(saved.getFtpUsed()).isEqualTo(235);
        assertThat(saved.getIntensityFactor()).isEqualTo(0.85);
        assertThat(saved.getTrainingLoad()).isEqualTo(72.0);
        verify(gemini).analyzeWorkout(any(), anyList(), anyList(), anyList(), eq(235), anyInt());
        verify(powerCurves).buildReportSet(any(), anyList(), eq(235));
    }

    @Test
    void icuFtpOfActivityWins() {
        when(intervals.getActivityZones("i1")).thenReturn(new ActivityZonesDto(List.of(), List.of(), true, 240));

        service.analyzeActivity("i1", activity("VirtualRide"));

        assertThat(savedAnalysis().getFtpUsed()).isEqualTo(240);
    }

    @Test
    void outdoorRideUsesOutdoorFtp() {
        when(intervals.getActivityZones("i1")).thenReturn(new ActivityZonesDto(List.of(), List.of(), false, null));

        service.analyzeActivity("i1", activity("Ride"));

        ActivityAnalysis saved = savedAnalysis();
        assertThat(saved.getIndoor()).isFalse();
        assertThat(saved.getFtpUsed()).isEqualTo(250);
        assertThat(saved.getTrainingLoad()).isEqualTo(64.0);
    }

    @Test
    void fallsBackToActivityTypeWhenActivityCouldNotBeLoaded() {
        when(intervals.getActivityZones("i1")).thenReturn(new ActivityZonesDto(List.of(), List.of(), null, null));

        service.analyzeActivity("i1", activity("VirtualRide"));

        ActivityAnalysis saved = savedAnalysis();
        assertThat(saved.getIndoor()).isTrue();
        assertThat(saved.getFtpUsed()).isEqualTo(235);
    }
}
