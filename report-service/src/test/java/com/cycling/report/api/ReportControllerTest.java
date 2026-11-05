package com.cycling.report.api;

import com.cycling.intervals.client.IntervalsUnavailableException;
import com.cycling.report.analysis.AnalysisJob;
import com.cycling.report.analysis.AnalysisJobService;
import com.cycling.report.analysis.AnalysisService;
import com.cycling.report.analysis.IntervalsData;
import com.cycling.report.analysis.ReportNotFoundException;
import com.cycling.report.analysis.ReportResendService;
import com.cycling.report.analysis.ReportResendService.Outcome;
import com.cycling.report.analysis.ReportResendService.Result;
import com.cycling.report.ftp.FtpDetection;
import com.cycling.report.ftp.FtpDetectionService;
import com.cycling.web.IntervalsProblemHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReportControllerTest {

    private final IntervalsData intervals = mock(IntervalsData.class);
    private final AnalysisService analysisService = mock(AnalysisService.class);
    private final AnalysisJobService jobService = mock(AnalysisJobService.class);
    private final ReportResendService resend = mock(ReportResendService.class);
    private final FtpDetectionService ftpDetection = mock(FtpDetectionService.class);
    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new ReportController(intervals, analysisService, jobService, resend, ftpDetection))
            .setControllerAdvice(new IntervalsProblemHandler())
            .build();

    private static ActivityDto activity(String id) {
        ActivityDto a = new ActivityDto();
        a.setId(id);
        a.setName("Runde " + id);
        return a;
    }

    @Test
    void activitiesCarryAnalyzedFlag() throws Exception {
        when(intervals.getActivities("2026-11-01", "2026-11-03")).thenReturn(List.of(activity("i1"), activity("i2")));
        when(analysisService.getAnalyzedIds()).thenReturn(List.of("i2"));

        mvc.perform(get("/api/reports/activities").param("from", "2026-11-01").param("to", "2026-11-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("i1"))
                .andExpect(jsonPath("$[0].analyzed").value(false))
                .andExpect(jsonPath("$[1].analyzed").value(true));
    }

    @Test
    void activitiesAnswerBadGatewayWhenIntervalsFails() throws Exception {
        when(intervals.getActivities(any(), any())).thenThrow(new IntervalsUnavailableException(
                org.springframework.http.HttpStatus.BAD_GATEWAY, "INTERVALS_AUTH", "API-Key pruefen", null, null));

        mvc.perform(get("/api/reports/activities").param("from", "2026-11-01").param("to", "2026-11-03"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("INTERVALS_AUTH"));
    }

    @Test
    void startAnalysisAnswersAcceptedWithJob() throws Exception {
        when(jobService.start(eq("i1"), any())).thenReturn(new AnalysisJob("job-1", "i1", "{}", LocalDateTime.now()));

        mvc.perform(post("/api/reports/i1/analysis").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"i1\",\"name\":\"Runde\",\"analyzed\":false}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value("job-1"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void unknownJobAnswersNotFound() throws Exception {
        when(jobService.find("nope")).thenReturn(Optional.empty());

        mvc.perform(get("/api/reports/analysis-jobs/nope")).andExpect(status().isNotFound());
    }

    @Test
    void neverAnalyzedActivityHasNoLatestJob() throws Exception {
        when(jobService.latestFor("i9")).thenReturn(Optional.empty());

        mvc.perform(get("/api/reports/i9/analysis-job")).andExpect(status().isNotFound());
    }

    @Test
    void missingReportAnswersNotFound() throws Exception {
        when(analysisService.getReport("i9")).thenThrow(new ReportNotFoundException("i9"));

        mvc.perform(get("/api/reports/i9")).andExpect(status().isNotFound());
    }

    @Test
    void resendAnswersAcceptedWithAllActiveChannels() throws Exception {
        when(resend.resend("i1")).thenReturn(new Outcome(Result.QUEUED, List.of("MAIL", "DISCORD")));

        mvc.perform(post("/api/reports/i1/resend"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("queued"))
                .andExpect(jsonPath("$.channels[1]").value("DISCORD"));
    }

    @Test
    void resendAnswersConflictWhenNoChannelIsActive() throws Exception {
        when(resend.resend("i1")).thenReturn(new Outcome(Result.DISABLED, List.of()));

        mvc.perform(post("/api/reports/i1/resend"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Kein Versandkanal aktiv."));
    }

    @Test
    void resendAnswersNotFoundForUnknownReport() throws Exception {
        when(resend.resend("nope")).thenReturn(new Outcome(Result.NOT_FOUND, List.of()));

        mvc.perform(post("/api/reports/nope/resend")).andExpect(status().isNotFound());
    }

    @Test
    void resendAnswersServiceUnavailableWhenDeliveryServiceIsUnknown() throws Exception {
        when(resend.resend("i1")).thenReturn(new Outcome(Result.UNAVAILABLE, List.of()));

        mvc.perform(post("/api/reports/i1/resend")).andExpect(status().isServiceUnavailable());
    }

    @Test
    void resendAnswersServiceUnavailableWithReasonWhenReportCannotBeLoaded() throws Exception {
        when(resend.resend("i1")).thenReturn(new Outcome(Result.REPORT_UNAVAILABLE, List.of()));

        mvc.perform(post("/api/reports/i1/resend"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("unavailable"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Report konnte nicht geladen werden")));
    }

    @Test
    void streamsReturnOnlyTheTimeSeries() throws Exception {
        when(intervals.getStreams("i1")).thenReturn(new StreamDataDto(List.of(), List.of(200, 210), List.of(140, 141),
                List.of(), List.of(), List.of(0, 1), List.of()));

        mvc.perform(get("/api/reports/i1/streams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.watts[1]").value(210))
                .andExpect(jsonPath("$.time[1]").value(1));
    }

    @Test
    void streamsAnswerNotFoundForUnknownActivity() throws Exception {
        when(intervals.getStreams("x")).thenThrow(new IntervalsUnavailableException(
                org.springframework.http.HttpStatus.NOT_FOUND, "NOT_FOUND", "unbekannt", null, null));

        mvc.perform(get("/api/reports/x/streams")).andExpect(status().isNotFound());
    }

    @Test
    void startFtpDetectionAnswersAcceptedWithPendingStatus() throws Exception {
        when(ftpDetection.start("i1")).thenReturn(new FtpDetection("i1", "r1", LocalDateTime.now()));

        mvc.perform(post("/api/reports/i1/ftp-detection"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.activityId").value("i1"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void ftpDetectionReturnsDetectedValues() throws Exception {
        FtpDetection detection = new FtpDetection("i1", "r1", LocalDateTime.now());
        detection.markDetected(266, 172, 182, 280, 1200, LocalDateTime.now());
        when(ftpDetection.find("i1")).thenReturn(Optional.of(detection));

        mvc.perform(get("/api/reports/i1/ftp-detection"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DETECTED"))
                .andExpect(jsonPath("$.ftp").value(266))
                .andExpect(jsonPath("$.lthr").value(172))
                .andExpect(jsonPath("$.maxHr").value(182))
                .andExpect(jsonPath("$.windowStartSeconds").value(1200));
    }

    @Test
    void ftpDetectionAnswersNotFoundWhenNeverStarted() throws Exception {
        when(ftpDetection.find("i1")).thenReturn(Optional.empty());

        mvc.perform(get("/api/reports/i1/ftp-detection")).andExpect(status().isNotFound());
    }
}
