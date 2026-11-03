package com.cycling.service;

import com.cycling.dto.ActivityDto;
import com.cycling.dto.ReportDto;
import com.cycling.dto.StreamDataDto;
import com.cycling.dto.ZoneDistributionDto;
import com.cycling.messaging.MessagePublisher;
import com.cycling.messaging.MessagingException;
import com.cycling.messaging.Topics;
import com.cycling.messaging.contract.AnalysisRequest;
import com.cycling.messaging.contract.AnalysisResult;
import com.cycling.messaging.contract.ReportDeliveryRequest;
import com.cycling.model.ActivityAnalysis;
import com.cycling.model.AnalysisJob;
import com.cycling.model.AnalysisJobStatus;
import com.cycling.repository.AnalysisJobRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalysisJobServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-02T10:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    private final AnalysisService analysis = mock(AnalysisService.class);
    private final AnalysisJobRepository repository = mock(AnalysisJobRepository.class);
    private final MessagePublisher publisher = mock(MessagePublisher.class);
    private final Map<String, AnalysisJob> stored = new HashMap<>();
    private final AnalysisJobService service = new AnalysisJobService(analysis, repository, publisher,
            new ObjectMapper(), CLOCK, Duration.ofMinutes(15));

    @BeforeEach
    void setUp() {
        when(repository.save(any())).thenAnswer(inv -> {
            AnalysisJob job = inv.getArgument(0);
            stored.put(job.getId(), job);
            return job;
        });
        when(repository.findById(any())).thenAnswer(inv -> Optional.ofNullable(stored.get(inv.<String>getArgument(0))));
        when(repository.findFirstByActivityIdAndStatusOrderByCreatedAtDesc(any(), any())).thenReturn(Optional.empty());
    }

    private static ActivityDto activity() {
        ActivityDto dto = new ActivityDto();
        dto.setId("i1");
        dto.setName("Runde");
        dto.setMovingTime(3600);
        dto.setNormalizedPower(210.0);
        return dto;
    }

    private static PreparedAnalysis prepared() {
        ActivityAnalysis a = new ActivityAnalysis();
        a.setActivityId("i1");
        a.setActivityName("Runde");
        a.setActivityDate(LocalDateTime.of(2026, 11, 1, 9, 30));
        a.setIndoor(false);
        a.setFtpUsed(250);
        ActivityAnalysis prev = new ActivityAnalysis();
        prev.setActivityName("Vorher");
        return new PreparedAnalysis(a, null,
                List.of(new ZoneDistributionDto("Z2", "Ausdauer", 1800, 50.0, "#0f0")), List.of(),
                List.of(prev), 250, 190);
    }

    /** Fertiger Report, wie ihn AnalysisService.complete liefert. */
    private static ReportDto report() {
        ActivityAnalysis a = prepared().analysis();
        a.setOverallScore(8);
        a.setSummary("z");
        StreamDataDto streams = new StreamDataDto(List.of(0.0, 10.0), List.of(150, 160), List.of(120, 125),
                List.of(8.0, 8.1), List.of(100.0, 101.0), List.of(0, 1), List.of());
        return new ReportDto(a, streams, List.of(new ZoneDistributionDto("Z2", "Ausdauer", 1800, 50.0, "#0f0")),
                List.of(), List.of(), null);
    }

    private static AnalysisResult success(String jobId) {
        return new AnalysisResult(jobId, true, null, 8, "s", "a", "p", "z");
    }

    @Test
    void startSavesJobAndPublishesRequestWithJobIdAsCorrelation() {
        when(analysis.prepare(eq("i1"), any())).thenReturn(prepared());

        AnalysisJob job = service.start("i1", activity());

        assertThat(job.getStatus()).isEqualTo(AnalysisJobStatus.PENDING);
        ArgumentCaptor<AnalysisRequest> captor = ArgumentCaptor.forClass(AnalysisRequest.class);
        verify(publisher).publish(eq(Topics.AI_ANALYSIS_REQUEST), captor.capture());
        AnalysisRequest request = captor.getValue();
        assertThat(request.requestId()).isEqualTo(job.getId());
        assertThat(request.ftp()).isEqualTo(250);
        assertThat(request.workout().activityDate()).isEqualTo("2026-11-01T09:30");
        assertThat(request.powerZones()).singleElement().satisfies(z -> assertThat(z.percentage()).isEqualTo(50.0));
        assertThat(request.previous()).extracting(w -> w.activityName()).containsExactly("Vorher");
    }

    @Test
    void startReturnsRunningJobInsteadOfStartingAgain() {
        AnalysisJob running = new AnalysisJob("j-old", "i1", "{}", NOW);
        when(repository.findFirstByActivityIdAndStatusOrderByCreatedAtDesc("i1", AnalysisJobStatus.PENDING))
                .thenReturn(Optional.of(running));

        assertThat(service.start("i1", activity())).isSameAs(running);
        verify(publisher, never()).publish(any(), any());
    }

    @Test
    void brokerOutageMarksJobFailed() {
        when(analysis.prepare(eq("i1"), any())).thenReturn(prepared());
        doThrow(new MessagingException("nicht verbunden", null)).when(publisher).publish(any(), any());

        AnalysisJob job = service.start("i1", activity());

        assertThat(job.getStatus()).isEqualTo(AnalysisJobStatus.FAILED);
        assertThat(job.getError()).contains("nicht verbunden");
    }

    @Test
    void successfulResultStoresAnalysisAndRequestsDelivery() {
        when(analysis.prepare(eq("i1"), any())).thenReturn(prepared());
        AnalysisJob job = service.start("i1", activity());
        when(analysis.complete(any(), eq(success(job.getId())))).thenReturn(report());

        service.complete(success(job.getId()));

        assertThat(stored.get(job.getId()).getStatus()).isEqualTo(AnalysisJobStatus.DONE);
        ArgumentCaptor<ReportDeliveryRequest> captor = ArgumentCaptor.forClass(ReportDeliveryRequest.class);
        verify(publisher).publish(eq(Topics.REPORT_READY), captor.capture());
        ReportDeliveryRequest request = captor.getValue();
        assertThat(request.requestId()).isEqualTo(job.getId());
        assertThat(request.activityId()).isEqualTo("i1");
        assertThat(request.report().analysis().activityName()).isEqualTo("Runde");
        assertThat(request.report().analysis().overallScore()).isEqualTo(8);
        assertThat(request.report().analysis().activityDate()).isEqualTo(LocalDateTime.of(2026, 11, 1, 9, 30));
        assertThat(request.report().streams().watts()).containsExactly(150, 160);
        assertThat(request.report().powerZones()).singleElement().satisfies(z -> assertThat(z.zoneName()).isEqualTo("Z2"));
        assertThat(request.report().powerCurveSet()).isNull();
    }

    @Test
    void storedActivityKeepsNormalizedPowerForCompletion() {
        when(analysis.prepare(eq("i1"), any())).thenReturn(prepared());
        AnalysisJob job = service.start("i1", activity());
        when(analysis.complete(any(), any())).thenReturn(report());

        service.complete(success(job.getId()));

        ArgumentCaptor<ActivityDto> captor = ArgumentCaptor.forClass(ActivityDto.class);
        verify(analysis, org.mockito.Mockito.times(2)).prepare(eq("i1"), captor.capture());
        ActivityDto restored = captor.getAllValues().get(1);
        assertThat(restored.name()).isEqualTo("Runde");
        assertThat(restored.movingTime()).isEqualTo(3600);
        assertThat(restored.normalizedPower()).isEqualTo(210.0);
    }

    @Test
    void duplicateResultIsIgnored() {
        AnalysisJob done = new AnalysisJob("j1", "i1", "{}", NOW);
        done.markDone(NOW);
        stored.put("j1", done);

        service.complete(success("j1"));

        verify(analysis, never()).complete(any(), any());
        verify(publisher, never()).publish(eq(Topics.REPORT_READY), any());
    }

    @Test
    void brokerOutageOnDeliveryRequestStillMarksJobDone() {
        stored.put("j1", new AnalysisJob("j1", "i1", "{\"id\":\"i1\"}", NOW));
        when(analysis.complete(any(), any())).thenReturn(report());
        doThrow(new MessagingException("weg", null)).when(publisher).publish(eq(Topics.REPORT_READY), any());

        service.complete(success("j1"));

        assertThat(stored.get("j1").getStatus()).isEqualTo(AnalysisJobStatus.DONE);
    }

    @Test
    void failedResultMarksJobFailed() {
        stored.put("j1", new AnalysisJob("j1", "i1", "{}", NOW));

        service.complete(AnalysisResult.failed("j1", "HTTP 429"));

        assertThat(stored.get("j1").getStatus()).isEqualTo(AnalysisJobStatus.FAILED);
        assertThat(stored.get("j1").getError()).contains("HTTP 429");
    }

    @Test
    void lateResultAfterTimeoutIsStillApplied() {
        AnalysisJob expired = new AnalysisJob("j1", "i1", "{\"id\":\"i1\"}", NOW.minusMinutes(30));
        expired.markFailed("Timeout", NOW);
        stored.put("j1", expired);
        when(analysis.complete(any(), any())).thenReturn(report());

        service.complete(success("j1"));

        assertThat(stored.get("j1").getStatus()).isEqualTo(AnalysisJobStatus.DONE);
    }

    @Test
    void staleJobsAreExpired() {
        AnalysisJob stale = new AnalysisJob("j1", "i1", "{}", NOW.minusMinutes(20));
        when(repository.findByStatusAndCreatedAtBefore(AnalysisJobStatus.PENDING, NOW.minusMinutes(15)))
                .thenReturn(List.of(stale));

        service.expireStaleJobs();

        assertThat(stale.getStatus()).isEqualTo(AnalysisJobStatus.FAILED);
        assertThat(stale.getError()).contains("15 Minuten");
    }
}
