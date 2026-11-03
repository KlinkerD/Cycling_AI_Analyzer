package com.cycling.controller;

import com.cycling.dto.AthleteZonesDto;
import com.cycling.dto.DailyRecommendationDto;
import com.cycling.dto.WellnessPointDto;
import com.cycling.service.IntervalsClient;
import com.cycling.service.RecommendationStore;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Endpoints für die Übersichts-/Overview-Seite:
 * - Wellness (CTL/ATL/Form) für Fitness-Chart
 * - Athleten-Zonen (Power + HF + Schwellenwerte)
 *
 * Die Daten cached der intervals-service. Mit {@code refresh=true} werden dessen Caches vorher geleert.
 */
@RestController
@io.swagger.v3.oas.annotations.tags.Tag(name = "Overview")
@RequestMapping("/api/overview")
public class OverviewController {

    private final IntervalsClient intervalsClient;
    private final RecommendationStore recommendationStore;

    public OverviewController(IntervalsClient intervalsClient,
                              RecommendationStore recommendationStore) {
        this.intervalsClient = intervalsClient;
        this.recommendationStore = recommendationStore;
    }

    @GetMapping("/wellness")
    public ResponseEntity<List<WellnessPointDto>> getWellness(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "false") boolean refresh
    ) {
        if (refresh) {
            intervalsClient.evictCaches();
        }
        return ResponseEntity.ok(intervalsClient.getWellness(
                from.format(DateTimeFormatter.ISO_DATE), to.format(DateTimeFormatter.ISO_DATE)));
    }

    @GetMapping("/zones")
    public ResponseEntity<AthleteZonesDto> getZones(
            @RequestParam(required = false, defaultValue = "false") boolean refresh
    ) {
        if (refresh) {
            intervalsClient.evictCaches();
        }
        return ResponseEntity.ok(intervalsClient.getAthleteZones());
    }

    /** Aktuelle Tagesempfehlung vom recommendation-service (bei Kaltstart wird einmal neu angefragt). */
    @GetMapping("/recommendation")
    public ResponseEntity<DailyRecommendationDto> getRecommendation() {
        return ResponseEntity.ok(recommendationStore.current());
    }

    /** Laesst den recommendation-service sofort neu rechnen (Refresh-Button) und wartet kurz auf das Ergebnis. */
    @PostMapping("/recommendation/refresh")
    public ResponseEntity<DailyRecommendationDto> refreshRecommendation() {
        return ResponseEntity.ok(recommendationStore.refresh());
    }
}
