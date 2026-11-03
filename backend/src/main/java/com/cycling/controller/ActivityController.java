package com.cycling.controller;

import com.cycling.dto.ActivityDto;
import com.cycling.dto.AnalysisJobDto;
import com.cycling.dto.ResendResultDto;
import com.cycling.dto.AthleteThresholdsDto;
import com.cycling.dto.ReportDto;
import com.cycling.service.AnalysisJobService;
import com.cycling.service.AnalysisService;
import com.cycling.service.IntervalsService;
import com.cycling.service.ReportResendService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@Tag(name = "Activities")
@RequestMapping("/api/activities")
public class ActivityController {

    private final IntervalsService intervalsService;
    private final AnalysisService analysisService;
    private final AnalysisJobService jobService;
    private final ReportResendService resendService;

    public ActivityController(IntervalsService intervalsService,
                              AnalysisService analysisService,
                              AnalysisJobService jobService,
                              ReportResendService resendService) {
        this.intervalsService = intervalsService;
        this.analysisService = analysisService;
        this.jobService = jobService;
        this.resendService = resendService;
    }

    /**
     * GET /api/activities?from=2024-01-01&to=2024-12-31
     * Liefert Rad-Aktivitäten aus intervals.icu für den gewählten Zeitraum.
     */
    @GetMapping
    public ResponseEntity<List<ActivityDto>> getActivities(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        String fromStr = from.format(DateTimeFormatter.ISO_DATE);
        String toStr = to.format(DateTimeFormatter.ISO_DATE);
        List<ActivityDto> activities = intervalsService.getActivities(fromStr, toStr);
        return ResponseEntity.ok(activities);
    }

    /**
     * POST /api/activities/{id}/analyze
     * Startet die KI-Analyse asynchron (Body: ActivityDto). Antwortet sofort mit 202 und dem Job;
     * den Fortschritt fragt das Frontend ueber GET /analysis-jobs/{jobId} ab. Laeuft schon eine
     * Analyse fuer die Aktivitaet, kommt deren Job zurueck. Versand stoesst die Fertigstellung an.
     */
    @PostMapping("/{id}/analyze")
    @ApiResponse(responseCode = "202", description = "Analyse gestartet (oder laeuft bereits)")
    public ResponseEntity<AnalysisJobDto> analyzeActivity(
            @PathVariable String id,
            @RequestBody ActivityDto activityDto
    ) {
        return ResponseEntity.accepted().body(AnalysisJobDto.from(jobService.start(id, activityDto)));
    }

    /** GET /api/activities/analysis-jobs/{jobId} – Status eines Analyseauftrags. */
    @GetMapping("/analysis-jobs/{jobId}")
    @ApiResponse(responseCode = "200", description = "Job gefunden")
    @ApiResponse(responseCode = "404", description = "Unbekannter Job", content = @Content)
    public ResponseEntity<AnalysisJobDto> getAnalysisJob(@PathVariable String jobId) {
        return jobService.find(jobId)
                .map(job -> ResponseEntity.ok(AnalysisJobDto.from(job)))
                .orElse(ResponseEntity.notFound().build());
    }

    /** GET /api/activities/{id}/analysis-job – juengster Analyseauftrag einer Aktivitaet (z. B. nach Reload). */
    @GetMapping("/{id}/analysis-job")
    @ApiResponse(responseCode = "200", description = "Juengster Job")
    @ApiResponse(responseCode = "404", description = "Noch nie analysiert", content = @Content)
    public ResponseEntity<AnalysisJobDto> getLatestAnalysisJob(@PathVariable String id) {
        return jobService.latestFor(id)
                .map(job -> ResponseEntity.ok(AnalysisJobDto.from(job)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/activities/athlete-thresholds
     * Gibt FTP, LTHR und Max-HR des Athleten zurück.
     */
    @GetMapping("/athlete-thresholds")
    public ResponseEntity<AthleteThresholdsDto> getAthleteThresholds() {
        return ResponseEntity.ok(intervalsService.getAthleteThresholds());
    }

    /**
     * GET /api/activities/analyzed-ids
     * Gibt alle Aktivitäts-IDs zurück, für die bereits eine KI-Analyse gespeichert ist.
     */
    @GetMapping("/analyzed-ids")
    public ResponseEntity<List<String>> getAnalyzedIds() {
        return ResponseEntity.ok(analysisService.getAnalyzedIds());
    }

    /**
     * GET /api/activities/{id}/report
     * Lädt einen gespeicherten Report aus der Datenbank.
     */
    @GetMapping("/{id}/report")
    @ApiResponse(responseCode = "200", description = "Gespeicherter Report")
    @ApiResponse(responseCode = "404", description = "Kein Report vorhanden", content = @Content)
    public ResponseEntity<ReportDto> getReport(@PathVariable String id) {
        try {
            ReportDto report = analysisService.getReport(id);
            return ResponseEntity.ok(report);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * POST /api/activities/{id}/resend
     * Beauftragt den report-service, den gespeicherten Report an alle aktiven Kanaele
     * (Mail, Discord) zu senden. 202 heisst "angestossen", nicht "zugestellt".
     */
    @PostMapping("/{id}/resend")
    @ApiResponse(responseCode = "202", description = "Versand angestossen")
    @ApiResponse(responseCode = "404", description = "Kein Report vorhanden", content = @Content)
    @ApiResponse(responseCode = "409", description = "Kein Versandkanal aktiv",
            content = @Content(schema = @Schema(implementation = ResendResultDto.class)))
    @ApiResponse(responseCode = "503", description = "Versand-Service nicht erreichbar",
            content = @Content(schema = @Schema(implementation = ResendResultDto.class)))
    public ResponseEntity<ResendResultDto> resendReport(@PathVariable String id) {
        ReportResendService.Outcome outcome = resendService.resend(id);
        return switch (outcome.result()) {
            case QUEUED -> ResponseEntity.accepted().body(ResendResultDto.queued(outcome.channels()));
            case DISABLED -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ResendResultDto.failed("disabled", "Kein Versandkanal aktiv."));
            case UNAVAILABLE -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ResendResultDto.failed("unavailable", "Versand-Service nicht erreichbar."));
            case NOT_FOUND -> ResponseEntity.notFound().build();
        };
    }
}
