package com.cycling.controller;

import com.cycling.delivery.DeliveryChannel;
import com.cycling.delivery.ReportDispatcher;
import com.cycling.dto.AnalysisJobDto;
import com.cycling.model.AnalysisJob;
import com.cycling.service.AnalysisJobService;
import com.cycling.service.AnalysisService;
import com.cycling.service.IntervalsService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ActivityControllerDeliveryTest {

    private final ReportDispatcher dispatcher = mock(ReportDispatcher.class);
    private final AnalysisService analysisService = mock(AnalysisService.class);
    private final AnalysisJobService jobService = mock(AnalysisJobService.class);
    private final ActivityController controller = new ActivityController(
            mock(IntervalsService.class), analysisService, jobService, dispatcher);

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
        when(dispatcher.resend("i1")).thenReturn(List.of(DeliveryChannel.MAIL, DeliveryChannel.DISCORD));

        ResponseEntity<Map<String, Object>> response = controller.resendReport("i1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).containsEntry("channels", List.of("MAIL", "DISCORD"));
    }

    @Test
    void resendAnswersConflictWhenNoChannelIsActive() {
        when(dispatcher.resend("i1")).thenReturn(List.of());

        ResponseEntity<Map<String, Object>> response = controller.resendReport("i1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("message", "Kein Versandkanal aktiv.");
    }

    @Test
    void resendAnswersNotFoundForUnknownReport() {
        when(dispatcher.resend("nope")).thenThrow(new RuntimeException("Kein Report für Aktivität nope gefunden."));

        ResponseEntity<Map<String, Object>> response = controller.resendReport("nope");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
