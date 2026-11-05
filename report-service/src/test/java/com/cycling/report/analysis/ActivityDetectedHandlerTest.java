package com.cycling.report.analysis;

import com.cycling.report.api.ActivityDto;
import com.cycling.messaging.contract.ActivityDetected;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActivityDetectedHandlerTest {

    private final AnalysisJobService jobs = mock(AnalysisJobService.class);
    private final ActivityAnalysisRepository repository = mock(ActivityAnalysisRepository.class);
    private final ActivityDetectedHandler handler = new ActivityDetectedHandler(jobs, repository);

    private static ActivityDetected detected(String id) {
        return new ActivityDetected(id, "Fahrt " + id, "2026-11-02T08:00:00", "Ride", 30000.0, 3600, 3700,
                180.0, 195.0, 500.0, 140.0, 170.0, 8.0, 200.0, "2026-11-02T07:00:00Z");
    }

    @Test
    void startsJobWithActivitySummary() {
        when(repository.findAllActivityIds()).thenReturn(List.of());
        when(jobs.runningActivityIds()).thenReturn(Set.of());
        when(jobs.start(any(), any())).thenReturn(new AnalysisJob("j", "i1", "{}", LocalDateTime.now()));

        handler.onActivityDetected(detected("i1"));

        ArgumentCaptor<ActivityDto> activity = ArgumentCaptor.forClass(ActivityDto.class);
        verify(jobs).start(eq("i1"), activity.capture());
        assertThat(activity.getValue().name()).isEqualTo("Fahrt i1");
        assertThat(activity.getValue().normalizedPower()).isEqualTo(195.0);
        assertThat(activity.getValue().movingTime()).isEqualTo(3600);
        assertThat(activity.getValue().startDateLocal()).isEqualTo("2026-11-02T08:00:00");
    }

    @Test
    void skipsActivityWithReport() {
        when(repository.findAllActivityIds()).thenReturn(List.of("done"));

        handler.onActivityDetected(detected("done"));

        verify(jobs, never()).start(any(), any());
    }

    @Test
    void skipsActivityWithRunningJob() {
        when(repository.findAllActivityIds()).thenReturn(List.of());
        when(jobs.runningActivityIds()).thenReturn(Set.of("running"));

        handler.onActivityDetected(detected("running"));

        verify(jobs, never()).start(any(), any());
    }

    @Test
    void ignoresMessageWithoutId() {
        handler.onActivityDetected(detected(null));

        verify(jobs, never()).start(any(), any());
    }
}
