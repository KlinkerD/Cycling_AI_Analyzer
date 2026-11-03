package com.cycling.training.api;

import com.cycling.intervals.client.IntervalsUnavailableException;
import com.cycling.training.recommendation.Recommendation;
import com.cycling.training.recommendation.RecommendationEngine;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

/**
 * REST-API der Tagesempfehlung (Overview-Karte). Laeuft der KI-Text noch ({@code textPending}), fragt das
 * Frontend nach ein paar Sekunden erneut ab.
 */
@RestController
@Tag(name = "Recommendation")
@RequestMapping("/api/recommendation")
public class RecommendationController {

    private static final Logger log = LoggerFactory.getLogger(RecommendationController.class);

    private final RecommendationEngine engine;

    public RecommendationController(RecommendationEngine engine) {
        this.engine = engine;
    }

    /** GET /api/recommendation - aktueller Stand; direkt nach dem Start wird einmal gerechnet. */
    @GetMapping
    @ApiResponse(responseCode = "200", description = "Aktuelle Empfehlung")
    @ApiResponse(responseCode = "503", description = "Noch keine Empfehlung und intervals-service nicht erreichbar",
            content = @Content)
    public ResponseEntity<DailyRecommendationDto> getRecommendation() {
        Optional<Recommendation> latest = engine.latest();
        return ResponseEntity.ok(DailyRecommendationDto.from(latest.isPresent() ? latest.get() : compute()));
    }

    /**
     * POST /api/recommendation/refresh - sofort neu rechnen (Refresh-Button). Liefert den ersten Stand mit
     * Fallback-Text; scheitert die Berechnung, bleibt der letzte Stand.
     */
    @PostMapping("/refresh")
    @ApiResponse(responseCode = "200", description = "Neue (oder bei Fehlern die letzte) Empfehlung")
    @ApiResponse(responseCode = "503", description = "Noch keine Empfehlung und intervals-service nicht erreichbar",
            content = @Content)
    public ResponseEntity<DailyRecommendationDto> refreshRecommendation() {
        return ResponseEntity.ok(DailyRecommendationDto.from(compute()));
    }

    /** Rechnet neu; ohne intervals-Daten gilt der letzte Stand, gibt es keinen, wird der Fehler zum 503. */
    private Recommendation compute() {
        try {
            return engine.compute(UUID.randomUUID().toString());
        } catch (IntervalsUnavailableException e) {
            Optional<Recommendation> latest = engine.latest();
            if (latest.isEmpty()) throw e;
            log.warn("Empfehlung nicht neu berechnet, letzter Stand bleibt: {}", e.getMessage());
            return latest.get();
        }
    }
}
