package com.cycling.report.api;

import com.cycling.types.ActivityId;
import java.util.UUID;

import com.cycling.report.analysis.AnalysisJobService;
import com.cycling.report.analysis.AnalysisService;
import com.cycling.report.analysis.IntervalsData;
import com.cycling.report.analysis.ReportNotFoundException;
import com.cycling.report.analysis.ReportResendService;
import com.cycling.report.ftp.FtpDetectionService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** REST-API fuer Aktivitaetsliste und Report-Seite. */
@RestController
@Tag(name = "Reports")
@RequestMapping("/api/reports")
public class ReportController {

    private final IntervalsData intervals;
    private final AnalysisService analysisService;
    private final AnalysisJobService jobService;
    private final ReportResendService resendService;
    private final FtpDetectionService ftpDetectionService;

    public ReportController(IntervalsData intervals, AnalysisService analysisService, AnalysisJobService jobService,
                            ReportResendService resendService, FtpDetectionService ftpDetectionService) {
        this.intervals = intervals;
        this.analysisService = analysisService;
        this.jobService = jobService;
        this.resendService = resendService;
        this.ftpDetectionService = ftpDetectionService;
    }

    /**
     * GET /api/reports/activities?from=2026-01-01&amp;to=2026-12-31
     * Rad-Aktivitaeten aus intervals.icu im Zeitraum; {@code analyzed} sagt, ob es schon einen Report gibt.
     */
    @GetMapping("/activities")
    public ResponseEntity<List<ActivityDto>> getActivities(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        List<ActivityDto> activities = intervals.getActivities(
                from.format(DateTimeFormatter.ISO_DATE), to.format(DateTimeFormatter.ISO_DATE));
        Set<ActivityId> analyzed = new HashSet<>(analysisService.getAnalyzedIds());
        activities.forEach(a -> a.setAnalyzed(analyzed.contains(a.id())));
        return ResponseEntity.ok(activities);
    }

    /**
     * POST /api/reports/{activityId}/analysis
     * Startet die KI-Analyse asynchron (Body: die Aktivitaet aus der Liste). Antwortet sofort mit 202 und dem
     * Job; den Fortschritt fragt das Frontend ueber GET /analysis-jobs/{jobId} ab. Laeuft schon eine Analyse
     * fuer die Aktivitaet, kommt deren Job zurueck. Ist sie fertig, wird der Report automatisch versendet.
     */
    @PostMapping("/{activityId}/analysis")
    @ApiResponse(responseCode = "202", description = "Analyse gestartet (oder laeuft bereits)")
    public ResponseEntity<AnalysisJobDto> startAnalysis(@PathVariable ActivityId activityId,
                                                        @RequestBody ActivityDto activity) {
        return ResponseEntity.accepted().body(AnalysisJobDto.from(jobService.start(activityId, activity)));
    }

    /** GET /api/reports/analysis-jobs/{jobId} - Status eines Analyseauftrags. */
    @GetMapping("/analysis-jobs/{jobId}")
    @ApiResponse(responseCode = "200", description = "Job gefunden")
    @ApiResponse(responseCode = "404", description = "Unbekannter Job", content = @Content)
    public ResponseEntity<AnalysisJobDto> getAnalysisJob(@PathVariable UUID jobId) {
        return jobService.find(jobId)
                .map(job -> ResponseEntity.ok(AnalysisJobDto.from(job)))
                .orElse(ResponseEntity.notFound().build());
    }

    /** GET /api/reports/{activityId}/analysis-job - juengster Analyseauftrag einer Aktivitaet (z. B. nach Reload). */
    @GetMapping("/{activityId}/analysis-job")
    @ApiResponse(responseCode = "200", description = "Juengster Job")
    @ApiResponse(responseCode = "404", description = "Noch nie analysiert", content = @Content)
    public ResponseEntity<AnalysisJobDto> getLatestAnalysisJob(@PathVariable ActivityId activityId) {
        return jobService.latestFor(activityId)
                .map(job -> ResponseEntity.ok(AnalysisJobDto.from(job)))
                .orElse(ResponseEntity.notFound().build());
    }

    /** GET /api/reports/{activityId} - gespeicherter Report mit frisch geladenen Streams und Zonen. */
    @GetMapping("/{activityId}")
    @ApiResponse(responseCode = "200", description = "Gespeicherter Report")
    @ApiResponse(responseCode = "404", description = "Kein Report vorhanden", content = @Content)
    public ResponseEntity<ReportDto> getReport(@PathVariable ActivityId activityId) {
        try {
            return ResponseEntity.ok(analysisService.getReport(activityId));
        } catch (ReportNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * POST /api/reports/{activityId}/resend
     * Sendet den gespeicherten Report an alle aktiven Kanaele (Mail, Discord). 202 heisst "angestossen",
     * nicht "zugestellt".
     */
    @PostMapping("/{activityId}/resend")
    @ApiResponse(responseCode = "202", description = "Versand angestossen")
    @ApiResponse(responseCode = "404", description = "Kein Report vorhanden", content = @Content)
    @ApiResponse(responseCode = "409", description = "Kein Versandkanal aktiv",
            content = @Content(schema = @Schema(implementation = ResendResultDto.class)))
    @ApiResponse(responseCode = "503", description = "Versand-Service nicht erreichbar oder Report nicht ladbar",
            content = @Content(schema = @Schema(implementation = ResendResultDto.class)))
    public ResponseEntity<ResendResultDto> resendReport(@PathVariable ActivityId activityId) {
        ReportResendService.Outcome outcome = resendService.resend(activityId);
        return switch (outcome.result()) {
            case QUEUED -> ResponseEntity.accepted().body(ResendResultDto.queued(outcome.channels()));
            case DISABLED -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ResendResultDto.failed("disabled", "Kein Versandkanal aktiv."));
            case UNAVAILABLE -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ResendResultDto.failed("unavailable", "Versand-Service nicht erreichbar."));
            case REPORT_UNAVAILABLE -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ResendResultDto.failed("unavailable",
                            "Report konnte nicht geladen werden (intervals-service nicht erreichbar)."));
            case NOT_FOUND -> ResponseEntity.notFound().build();
        };
    }

    /**
     * GET /api/reports/{activityId}/streams - nur die Zeitreihen einer Fahrt (ohne Analyse, Zonen, Trend).
     * Quelle fuer den ftp-service.
     */
    @GetMapping("/{activityId}/streams")
    @ApiResponse(responseCode = "200", description = "Streams der Fahrt")
    @ApiResponse(responseCode = "404", description = "Fahrt bei intervals.icu unbekannt", content = @Content)
    public ResponseEntity<StreamDataDto> getStreams(@PathVariable ActivityId activityId) {
        return ResponseEntity.ok(intervals.getStreams(activityId));
    }

    /**
     * POST /api/reports/{activityId}/ftp-detection
     * Startet die FTP-Test-Erkennung asynchron beim ftp-service. Antwortet sofort mit 202; den Status fragt
     * das Frontend ueber GET /{activityId}/ftp-detection ab. Laeuft schon eine Erkennung, kommt diese zurueck.
     */
    @PostMapping("/{activityId}/ftp-detection")
    @ApiResponse(responseCode = "202", description = "Erkennung gestartet (oder laeuft bereits)")
    public ResponseEntity<FtpDetectionDto> startFtpDetection(@PathVariable ActivityId activityId) {
        return ResponseEntity.accepted().body(FtpDetectionDto.from(ftpDetectionService.start(activityId)));
    }

    /** GET /api/reports/{activityId}/ftp-detection - Ergebnis bzw. Status der FTP-Test-Erkennung. */
    @GetMapping("/{activityId}/ftp-detection")
    @ApiResponse(responseCode = "200", description = "Ergebnis oder laufende Erkennung")
    @ApiResponse(responseCode = "404", description = "Noch nie ausgeloest", content = @Content)
    public ResponseEntity<FtpDetectionDto> getFtpDetection(@PathVariable ActivityId activityId) {
        return ftpDetectionService.find(activityId)
                .map(d -> ResponseEntity.ok(FtpDetectionDto.from(d)))
                .orElse(ResponseEntity.notFound().build());
    }
}
