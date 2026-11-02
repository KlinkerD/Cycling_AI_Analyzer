package com.cycling.service;

import com.cycling.dto.ActivityDto;
import com.cycling.model.AnalysisJob;
import com.cycling.repository.ActivityAnalysisRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduledAnalysisServiceTest {

    private final IntervalsService intervals = mock(IntervalsService.class);
    private final AnalysisJobService jobs = mock(AnalysisJobService.class);
    private final ActivityAnalysisRepository repository = mock(ActivityAnalysisRepository.class);
    private final ScheduledAnalysisService service = new ScheduledAnalysisService(intervals, jobs, repository);

    private static ActivityDto activity(String id) {
        ActivityDto dto = new ActivityDto();
        dto.setId(id);
        dto.setName("Fahrt " + id);
        return dto;
    }

    @Test
    void startsJobOnlyForActivitiesWithoutReportOrRunningJob() {
        ActivityDto fresh = activity("i1");
        when(intervals.getActivities(anyString(), anyString()))
                .thenReturn(List.of(fresh, activity("done"), activity("running")));
        when(repository.findAllActivityIds()).thenReturn(List.of("done"));
        when(jobs.runningActivityIds()).thenReturn(Set.of("running"));
        when(jobs.start(any(), any())).thenReturn(new AnalysisJob("j", "i1", "{}", LocalDateTime.now()));

        service.checkForNewActivities();

        verify(jobs).start("i1", fresh);
        verify(jobs, never()).start(eq("done"), any());
        verify(jobs, never()).start(eq("running"), any());
    }
}
