package com.cycling.report.analysis;

import com.cycling.intervals.client.IntervalsUnavailableException;
import com.cycling.report.ReportDelivery;
import com.cycling.report.analysis.ReportResendService.Result;
import com.cycling.report.api.AnalysisDto;
import com.cycling.report.api.ReportDto;
import com.cycling.report.api.StreamDataDto;
import com.cycling.messaging.contract.DeliveryServiceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportResendServiceTest {

    private final ActivityAnalysisRepository repository = mock(ActivityAnalysisRepository.class);
    private final AnalysisService analysisService = mock(AnalysisService.class);
    @SuppressWarnings("unchecked")
    private final Consumer<ReportDelivery> delivery = mock(Consumer.class);
    private final ReportResendService service = new ReportResendService(repository, analysisService, delivery);

    @BeforeEach
    void setUp() {
        ActivityAnalysis analysis = new ActivityAnalysis();
        analysis.setActivityId("i1");
        analysis.setActivityName("Runde");
        when(repository.findByActivityId("i1")).thenReturn(Optional.of(analysis));
        StreamDataDto streams = new StreamDataDto(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of());
        when(analysisService.getReport("i1"))
                .thenReturn(new ReportDto(AnalysisDto.from(analysis), streams, List.of(), List.of(), List.of(), null, List.of(), List.of(), List.of()));
    }

    @Test
    void queuesResendWithFreshRequestIdAndReportsChannels() {
        service.updateStatus(new DeliveryServiceStatus(true, List.of("MAIL", "DISCORD")));

        ReportResendService.Outcome first = service.resend("i1");
        service.resend("i1");

        assertThat(first.result()).isEqualTo(Result.QUEUED);
        assertThat(first.channels()).containsExactly("MAIL", "DISCORD");
        ArgumentCaptor<ReportDelivery> captor = ArgumentCaptor.forClass(ReportDelivery.class);
        verify(delivery, org.mockito.Mockito.times(2)).accept(captor.capture());
        assertThat(captor.getAllValues()).extracting(ReportDelivery::activityId).containsOnly("i1");
        assertThat(captor.getAllValues()).extracting(r -> r.report().analysis().activityName()).containsOnly("Runde");
        assertThat(captor.getAllValues().get(0).requestId()).isNotEqualTo(captor.getAllValues().get(1).requestId());
    }

    @Test
    void offlineDeliveryServiceStillGetsTheOrderViaBrokerSession() {
        service.updateStatus(new DeliveryServiceStatus(false, List.of("MAIL")));

        assertThat(service.resend("i1").result()).isEqualTo(Result.QUEUED);
    }

    @Test
    void unknownReportIsNotFound() {
        service.updateStatus(new DeliveryServiceStatus(true, List.of("MAIL")));

        assertThat(service.resend("nope").result()).isEqualTo(Result.NOT_FOUND);
        verify(delivery, never()).accept(any());
    }

    @Test
    void reportThatCannotBeLoadedIsReportedAndNothingIsSent() {
        service.updateStatus(new DeliveryServiceStatus(true, List.of("MAIL")));
        when(analysisService.getReport("i1")).thenThrow(new IntervalsUnavailableException(HttpStatus.SERVICE_UNAVAILABLE,
                IntervalsUnavailableException.SERVICE_UNREACHABLE, "intervals-service weg", null, null));

        assertThat(service.resend("i1").result()).isEqualTo(Result.REPORT_UNAVAILABLE);
        verify(delivery, never()).accept(any());
    }

    @Test
    void noStatusYetMeansUnavailable() {
        assertThat(service.resend("i1").result()).isEqualTo(Result.UNAVAILABLE);
    }

    @Test
    void noActiveChannelMeansDisabled() {
        service.updateStatus(new DeliveryServiceStatus(true, List.of()));

        assertThat(service.resend("i1").result()).isEqualTo(Result.DISABLED);
        verify(delivery, never()).accept(any());
        verify(analysisService, never()).getReport(any());
    }

    @Test
    void failingDeliveryMeansUnavailable() {
        service.updateStatus(new DeliveryServiceStatus(true, List.of("MAIL")));
        doThrow(new IllegalStateException("weg")).when(delivery).accept(any());

        assertThat(service.resend("i1").result()).isEqualTo(Result.UNAVAILABLE);
    }
}
