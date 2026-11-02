package com.cycling.controller;

import com.cycling.delivery.DeliveryChannel;
import com.cycling.delivery.ReportDispatcher;
import com.cycling.dto.ActivityDto;
import com.cycling.dto.AthleteThresholdsDto;
import com.cycling.dto.ReportDto;
import com.cycling.service.AnalysisService;
import com.cycling.service.IntervalsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/activities")
public class ActivityController {

    private static final Logger log = LoggerFactory.getLogger(ActivityController.class);

    private final IntervalsService intervalsService;
    private final AnalysisService analysisService;
    private final ReportDispatcher reportDispatcher;

    public ActivityController(IntervalsService intervalsService,
                              AnalysisService analysisService,
                              ReportDispatcher reportDispatcher) {
        this.intervalsService = intervalsService;
        this.analysisService = analysisService;
        this.reportDispatcher = reportDispatcher;
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
     * Startet die KI-Analyse für eine Aktivität. Gibt den vollständigen Report zurück.
     * Body: ActivityDto (Metadaten der Aktivität)
     */
    @PostMapping("/{id}/analyze")
    public ResponseEntity<ReportDto> analyzeActivity(
            @PathVariable String id,
            @RequestBody ActivityDto activityDto
    ) {
        ReportDto report = analysisService.analyzeActivity(id, activityDto);
        try {
            reportDispatcher.dispatch(report);
        } catch (RuntimeException e) {
            // Versand ist fail-soft: die Analyse ist gespeichert und wird trotzdem zurueckgegeben.
            log.warn("Report-Versand fuer {} konnte nicht angestossen werden: {}", id, e.getMessage());
        }
        return ResponseEntity.ok(report);
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
     * Sendet den gespeicherten Report an alle aktiven Kanaele (Mail, Discord).
     * Versand laeuft im Hintergrund - 202 heisst "angestossen", nicht "zugestellt".
     */
    @PostMapping("/{id}/resend")
    public ResponseEntity<Map<String, Object>> resendReport(@PathVariable String id) {
        List<DeliveryChannel> channels;
        try {
            channels = reportDispatcher.resend(id);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
        if (channels.isEmpty()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("status", "disabled", "message", "Kein Versandkanal aktiv."));
        }
        return ResponseEntity.accepted().body(Map.of(
                "status", "queued",
                "channels", channels.stream().map(Enum::name).toList()
        ));
    }
}

