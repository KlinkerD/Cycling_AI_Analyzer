package com.cycling.report.analysis;

import com.cycling.report.ReportDelivery;
import com.cycling.report.api.ActivityDto;
import com.cycling.report.api.AnalysisDto;
import com.cycling.report.api.ReportDto;
import com.cycling.report.api.StreamDataDto;
import com.cycling.report.api.ZoneDistributionDto;
import com.cycling.messaging.MessagePublisher;
import com.cycling.messaging.MessagingException;
import com.cycling.messaging.Topics;
import com.cycling.messaging.contract.AiRequest;
import com.cycling.messaging.contract.AiResult;
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
import java.util.function.Consumer;

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
    @SuppressWarnings("unchecked")
    private final Consumer<ReportDelivery> delivery = mock(Consumer.class);
    private final Map<String, AnalysisJob> stored = new HashMap<>();
    private final AnalysisJobService service = new AnalysisJobService(analysis, repository, publisher, delivery,
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
                List.of(prev), 250, 190, List.of(), null);
    }

    /** Fertiger Report, wie ihn AnalysisService.complete liefert. */
    private static ReportDto report() {
        ActivityAnalysis a = prepared().analysis();
        a.setOverallScore(8);
        a.setSummary("z");
        StreamDataDto streams = new StreamDataDto(List.of(0.0, 10.0), List.of(150, 160), List.of(120, 125),
                List.of(8.0, 8.1), List.of(100.0, 101.0), List.of(0, 1), List.of(), List.of(), List.of());
        return new ReportDto(AnalysisDto.from(a), streams, List.of(new ZoneDistributionDto("Z2", "Ausdauer", 1800, 50.0, "#0f0")),
                List.of(), List.of(), null, List.of(), List.of(), List.of());
    }

    private static final AiAssessment ASSESSMENT = new AiAssessment(8, "s", "a", "p", "z");

    private static AiResult success(String jobId) {
        return AiResult.ok(jobId, """
                {"score": 8, "strengths": "s", "anomalies": "a", "progress": "p", "summary": "z"}""");
    }

    @Test
    void startSavesJobAndPublishesRequestWithJobIdAsCorrelation() {
        when(analysis.prepare(eq("i1"), any())).thenReturn(prepared());

        AnalysisJob job = service.start("i1", activity());

        assertThat(job.getStatus()).isEqualTo(AnalysisJobStatus.PENDING);
        ArgumentCaptor<AiRequest> captor = ArgumentCaptor.forClass(AiRequest.class);
        verify(publisher).publish(eq("cycling/ai/request/report"), captor.capture());
        AiRequest request = captor.getValue();
        assertThat(request.requestId()).isEqualTo(job.getId());
        assertThat(request.json()).isTrue();
        assertThat(request.temperature()).isEqualTo(AnalysisPrompt.TEMPERATURE);
        assertThat(request.prompt()).isEqualTo(AnalysisPrompt.build(prepared()));
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
        when(analysis.complete(any(), eq(ASSESSMENT))).thenReturn(report());

        service.complete(success(job.getId()));

        assertThat(stored.get(job.getId()).getStatus()).isEqualTo(AnalysisJobStatus.DONE);
        ArgumentCaptor<ReportDelivery> captor = ArgumentCaptor.forClass(ReportDelivery.class);
        verify(delivery).accept(captor.capture());
        ReportDelivery request = captor.getValue();
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
        verify(delivery, never()).accept(any());
    }

    @Test
    void failingDeliveryStillMarksJobDone() {
        stored.put("j1", new AnalysisJob("j1", "i1", "{\"id\":\"i1\"}", NOW));
        when(analysis.complete(any(), any())).thenReturn(report());
        doThrow(new IllegalStateException("weg")).when(delivery).accept(any());

        service.complete(success("j1"));

        assertThat(stored.get("j1").getStatus()).isEqualTo(AnalysisJobStatus.DONE);
    }

    @Test
    void failedResultMarksJobFailed() {
        stored.put("j1", new AnalysisJob("j1", "i1", "{}", NOW));

        service.complete(AiResult.failed("j1", "HTTP 429"));

        assertThat(stored.get("j1").getStatus()).isEqualTo(AnalysisJobStatus.FAILED);
        assertThat(stored.get("j1").getError()).contains("HTTP 429");
    }

    @Test
    void unreadableAnswerMarksJobFailedWithoutSaving() {
        stored.put("j1", new AnalysisJob("j1", "i1", "{}", NOW));

        service.complete(AiResult.ok("j1", "Das ist kein JSON"));

        assertThat(stored.get("j1").getStatus()).isEqualTo(AnalysisJobStatus.FAILED);
        assertThat(stored.get("j1").getError()).contains("kein gueltiges JSON");
        verify(analysis, never()).complete(any(), any());
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
