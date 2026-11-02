package com.cycling.service;

import com.cycling.delivery.ReportDispatcher;
import com.cycling.dto.ActivityDto;
import com.cycling.dto.ReportDto;
import com.cycling.repository.ActivityAnalysisRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduledAnalysisServiceTest {

    private final IntervalsService intervals = mock(IntervalsService.class);
    private final AnalysisService analysis = mock(AnalysisService.class);
    private final ActivityAnalysisRepository repository = mock(ActivityAnalysisRepository.class);
    private final ReportDispatcher dispatcher = mock(ReportDispatcher.class);
    private final ScheduledAnalysisService service =
            new ScheduledAnalysisService(intervals, analysis, repository, dispatcher, duration -> { });

    @Test
    void dispatchesReportOfEveryNewlyAnalyzedActivity() {
        ActivityDto activity = mock(ActivityDto.class);
        when(activity.id()).thenReturn("i1");
        when(activity.name()).thenReturn("Morgenrunde");
        ReportDto report = mock(ReportDto.class);
        when(intervals.getActivities(anyString(), anyString())).thenReturn(List.of(activity));
        when(repository.findAllActivityIds()).thenReturn(List.of());
        when(analysis.analyzeActivity("i1", activity)).thenReturn(report);

        service.checkForNewActivities();

        verify(dispatcher).dispatch(report);
    }
}
