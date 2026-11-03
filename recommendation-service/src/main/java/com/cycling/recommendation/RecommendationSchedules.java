package com.cycling.recommendation;

import org.springframework.scheduling.annotation.Scheduled;

/** Zeitgesteuerte Arbeit der Engine: regelmaessig neu rechnen und haengende KI-Auftraege beenden. */
public class RecommendationSchedules {

    private final RecommendationEngine engine;

    public RecommendationSchedules(RecommendationEngine engine) {
        this.engine = engine;
    }

    /** Haelt die Overview-Karte tagsueber aktuell (Default alle 4 Stunden zur vollen Stunde). */
    @Scheduled(cron = "${recommendation.refresh.cron:0 0 */4 * * *}", zone = "${recommendation.zone:Europe/Berlin}")
    public void refresh() {
        engine.computeScheduled();
    }

    @Scheduled(fixedDelayString = "${recommendation.text-check-interval:10s}")
    public void expirePendingText() {
        engine.expirePending();
    }
}
