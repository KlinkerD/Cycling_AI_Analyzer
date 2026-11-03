package com.cycling.report.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Raeumt haengende Analyse-Jobs auf. Neue Aktivitaeten meldet der intervals-service per MQTT
 * (siehe {@link ActivityDetectedHandler}).
 */
@Service
public class ScheduledAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledAnalysisService.class);

    private final AnalysisJobService jobService;

    public ScheduledAnalysisService(AnalysisJobService jobService) {
        this.jobService = jobService;
    }

    /** Jobs ohne Antwort abbrechen, damit sie neu gestartet werden koennen. */
    @Scheduled(fixedDelay = 60 * 1000, initialDelay = 60 * 1000)
    public void expireStaleJobs() {
        try {
            jobService.expireStaleJobs();
        } catch (RuntimeException e) {
            log.error("Fehler beim Aufraeumen haengender Analyse-Jobs: {}", e.getMessage(), e);
        }
    }
}
