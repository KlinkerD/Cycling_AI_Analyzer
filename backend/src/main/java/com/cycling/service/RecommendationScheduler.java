package com.cycling.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Aktualisiert die Tagesempfehlung automatisch. Takt ueber den Config-Key
 * {@code recommendation.refresh.cron} (Default: alle 4 Stunden zur vollen Stunde).
 * Der manuelle Refresh-Button nutzt denselben Service-Aufruf.
 */
@Component
public class RecommendationScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecommendationScheduler.class);

    private final RecommendationService recommendationService;

    public RecommendationScheduler(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @Scheduled(cron = "${recommendation.refresh.cron:0 0 */4 * * *}")
    public void refreshRecommendation() {
        log.info("=== Scheduled: Aktualisiere Tagesempfehlung ===");
        try {
            recommendationService.computeAndStore();
        } catch (Exception e) {
            log.error("Scheduler-Fehler bei der Tagesempfehlung: {}", e.getMessage(), e);
        }
    }
}
