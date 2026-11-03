package com.cycling.service;

import com.cycling.dto.ReportDto;
import com.cycling.dto.StreamDataDto;
import com.cycling.messaging.MessagePublisher;
import com.cycling.messaging.MessagingException;
import com.cycling.messaging.Topics;
import com.cycling.messaging.contract.DeliveryServiceStatus;
import com.cycling.messaging.contract.ReportDeliveryRequest;
import com.cycling.model.ActivityAnalysis;
import com.cycling.repository.ActivityAnalysisRepository;
import com.cycling.service.ReportResendService.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportResendServiceTest {

    private final ActivityAnalysisRepository repository = mock(ActivityAnalysisRepository.class);
    private final AnalysisService analysisService = mock(AnalysisService.class);
    private final MessagePublisher publisher = mock(MessagePublisher.class);
    private final ReportResendService service = new ReportResendService(repository, analysisService, publisher);

    @BeforeEach
    void setUp() {
        ActivityAnalysis analysis = new ActivityAnalysis();
        analysis.setActivityId("i1");
        analysis.setActivityName("Runde");
        when(repository.findByActivityId("i1")).thenReturn(Optional.of(analysis));
        StreamDataDto streams = new StreamDataDto(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        when(analysisService.getReport("i1"))
                .thenReturn(new ReportDto(analysis, streams, List.of(), List.of(), List.of(), null));
    }

    @Test
    void queuesResendWithFreshRequestIdAndReportsChannels() {
        service.updateStatus(new DeliveryServiceStatus(true, List.of("MAIL", "DISCORD")));

        ReportResendService.Outcome first = service.resend("i1");
        service.resend("i1");

        assertThat(first.result()).isEqualTo(Result.QUEUED);
        assertThat(first.channels()).containsExactly("MAIL", "DISCORD");
        ArgumentCaptor<ReportDeliveryRequest> captor = ArgumentCaptor.forClass(ReportDeliveryRequest.class);
        verify(publisher, org.mockito.Mockito.times(2)).publish(eq(Topics.REPORT_RESEND), captor.capture());
        assertThat(captor.getAllValues()).extracting(ReportDeliveryRequest::activityId).containsOnly("i1");
        assertThat(captor.getAllValues()).extracting(r -> r.report().analysis().activityName()).containsOnly("Runde");
        assertThat(captor.getAllValues().get(0).requestId()).isNotEqualTo(captor.getAllValues().get(1).requestId());
    }

    @Test
    void offlineServiceStillGetsTheOrderViaBrokerSession() {
        service.updateStatus(new DeliveryServiceStatus(false, List.of("MAIL")));

        assertThat(service.resend("i1").result()).isEqualTo(Result.QUEUED);
    }

    @Test
    void unknownReportIsNotFound() {
        service.updateStatus(new DeliveryServiceStatus(true, List.of("MAIL")));

        assertThat(service.resend("nope").result()).isEqualTo(Result.NOT_FOUND);
        verify(publisher, never()).publish(any(), any());
    }

    @Test
    void reportThatCannotBeLoadedIsReportedAndNothingIsSent() {
        service.updateStatus(new DeliveryServiceStatus(true, List.of("MAIL")));
        when(analysisService.getReport("i1")).thenThrow(new IntervalsUnavailableException(HttpStatus.SERVICE_UNAVAILABLE,
                IntervalsUnavailableException.SERVICE_UNREACHABLE, "intervals-service weg", null, null));

        assertThat(service.resend("i1").result()).isEqualTo(Result.REPORT_UNAVAILABLE);
        verify(publisher, never()).publish(any(), any());
    }

    @Test
    void noStatusYetMeansUnavailable() {
        assertThat(service.resend("i1").result()).isEqualTo(Result.UNAVAILABLE);
    }

    @Test
    void noActiveChannelMeansDisabled() {
        service.updateStatus(new DeliveryServiceStatus(true, List.of()));

        assertThat(service.resend("i1").result()).isEqualTo(Result.DISABLED);
        verify(publisher, never()).publish(any(), any());
        verify(analysisService, never()).getReport(any());
    }

    @Test
    void brokerOutageMeansUnavailable() {
        service.updateStatus(new DeliveryServiceStatus(true, List.of("MAIL")));
        doThrow(new MessagingException("weg", null)).when(publisher).publish(any(), any());

        assertThat(service.resend("i1").result()).isEqualTo(Result.UNAVAILABLE);
    }
}
