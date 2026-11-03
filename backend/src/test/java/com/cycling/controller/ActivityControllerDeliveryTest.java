package com.cycling.controller;

import com.cycling.dto.AnalysisJobDto;
import com.cycling.model.AnalysisJob;
import com.cycling.service.AnalysisJobService;
import com.cycling.service.AnalysisService;
import com.cycling.service.IntervalsClient;
import com.cycling.service.ReportResendService;
import com.cycling.service.ReportResendService.Outcome;
import com.cycling.service.ReportResendService.Result;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ActivityControllerDeliveryTest {

    private final ReportResendService resend = mock(ReportResendService.class);
    private final AnalysisService analysisService = mock(AnalysisService.class);
    private final AnalysisJobService jobService = mock(AnalysisJobService.class);
    private final ActivityController controller = new ActivityController(
            mock(IntervalsClient.class), analysisService, jobService, resend);

    @Test
    void analyzeAnswersAcceptedWithJob() {
        com.cycling.dto.ActivityDto activity = mock(com.cycling.dto.ActivityDto.class);
        when(jobService.start("i1", activity)).thenReturn(new AnalysisJob("job-1", "i1", "{}", LocalDateTime.now()));

        ResponseEntity<AnalysisJobDto> response = controller.analyzeActivity("i1", activity);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().jobId()).isEqualTo("job-1");
        assertThat(response.getBody().status()).isEqualTo("PENDING");
    }

    @Test
    void unknownJobAnswersNotFound() {
        when(jobService.find("nope")).thenReturn(Optional.empty());

        assertThat(controller.getAnalysisJob("nope").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void resendAnswersAcceptedWithAllActiveChannels() {
        when(resend.resend("i1")).thenReturn(new Outcome(Result.QUEUED, List.of("MAIL", "DISCORD")));

        ResponseEntity<com.cycling.dto.ResendResultDto> response = controller.resendReport("i1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().channels()).containsExactly("MAIL", "DISCORD");
    }

    @Test
    void resendAnswersConflictWhenNoChannelIsActive() {
        when(resend.resend("i1")).thenReturn(new Outcome(Result.DISABLED, List.of()));

        ResponseEntity<com.cycling.dto.ResendResultDto> response = controller.resendReport("i1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().message()).isEqualTo("Kein Versandkanal aktiv.");
    }

    @Test
    void resendAnswersNotFoundForUnknownReport() {
        when(resend.resend("nope")).thenReturn(new Outcome(Result.NOT_FOUND, List.of()));

        ResponseEntity<com.cycling.dto.ResendResultDto> response = controller.resendReport("nope");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void resendAnswersServiceUnavailableWhenDeliveryServiceIsUnknown() {
        when(resend.resend("i1")).thenReturn(new Outcome(Result.UNAVAILABLE, List.of()));

        assertThat(controller.resendReport("i1").getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void resendAnswersServiceUnavailableWithReasonWhenReportCannotBeLoaded() {
        when(resend.resend("i1")).thenReturn(new Outcome(Result.REPORT_UNAVAILABLE, List.of()));

        ResponseEntity<com.cycling.dto.ResendResultDto> response = controller.resendReport("i1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().status()).isEqualTo("unavailable");
        assertThat(response.getBody().message()).contains("Report konnte nicht geladen werden");
    }
}
