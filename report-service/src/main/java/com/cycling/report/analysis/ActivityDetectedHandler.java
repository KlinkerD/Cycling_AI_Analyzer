package com.cycling.report.analysis;

import com.cycling.types.ActivityId;

import com.cycling.report.api.ActivityDto;
import com.cycling.messaging.contract.ActivityDetected;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Startet die KI-Analyse fuer neue Aktivitaeten, die der intervals-service meldet
 * ({@code cycling/intervals/activity/detected}). Meldungen koennen mehrfach kommen; Aktivitaeten mit
 * Report oder laufender Analyse werden uebersprungen. Der intervals-service meldet nur Fahrten der
 * letzten Tage, alte Aktivitaeten ohne Report bleiben also bewusst unanalysiert.
 */
public class ActivityDetectedHandler {

    private static final Logger log = LoggerFactory.getLogger(ActivityDetectedHandler.class);

    private final AnalysisJobService jobService;
    private final ActivityAnalysisRepository repository;

    public ActivityDetectedHandler(AnalysisJobService jobService, ActivityAnalysisRepository repository) {
        this.jobService = jobService;
        this.repository = repository;
    }

    public void onActivityDetected(ActivityDetected message) {
        ActivityId activityId = message.activityId();
        if (activityId == null) {
            log.warn("ActivityDetected ohne activityId verworfen");
            return;
        }
        if (repository.findAllActivityIds().contains(activityId)) {
            log.debug("Aktivitaet {} bereits analysiert", activityId);
            return;
        }
        if (jobService.runningActivityIds().contains(activityId)) {
            log.debug("Analyse fuer {} laeuft bereits", activityId);
            return;
        }
        log.info("Neue Aktivitaet '{}' ({}) - sende KI-Auftrag", message.name(), activityId);
        AnalysisJob job = jobService.start(activityId, ActivityDto.from(message));
        if (job.getStatus() == AnalysisJobStatus.FAILED) {
            log.error("Analyse von '{}' ({}) nicht gestartet: {}", message.name(), activityId, job.getError());
        }
    }
}
