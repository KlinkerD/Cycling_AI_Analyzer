package com.cycling.service;

import com.cycling.dto.ActivityDto;
import com.cycling.model.AnalysisJob;
import com.cycling.model.AnalysisJobStatus;
import com.cycling.repository.ActivityAnalysisRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Prüft alle 15 Minuten auf neue Aktivitäten der letzten 2 Tage und startet fuer jede ohne Report
 * (und ohne laufende Analyse) einen KI-Auftrag. Alte Aktivitäten ohne Report werden bewusst ignoriert.
 * Das Rate-Limit fuer Gemini haelt der ai-service ein, hier wird nur verschickt.
 */
@Service
public class ScheduledAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledAnalysisService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final IntervalsService intervalsService;
    private final AnalysisJobService jobService;
    private final ActivityAnalysisRepository repository;

    public ScheduledAnalysisService(IntervalsService intervalsService,
                                    AnalysisJobService jobService,
                                    ActivityAnalysisRepository repository) {
        this.intervalsService = intervalsService;
        this.jobService = jobService;
        this.repository = repository;
    }

    /**
     * fixedDelay = Wartezeit nach Ende des letzten Laufs (verhindert Überlappungen).
     * initialDelay = 60 Sekunden Wartezeit nach App-Start (Backend erst hochfahren lassen).
     */
    @Scheduled(fixedDelay = 15 * 60 * 1000, initialDelay = 60 * 1000)
    public void checkForNewActivities() {
        log.info("=== Scheduled: Prüfe auf neue Aktivitäten ===");
        try {
            // Nur letzte 2 Tage abfragen – damit werden keine alten Reports erzeugt
            String from = LocalDate.now().minusDays(2).format(DATE_FMT);
            String to   = LocalDate.now().plusDays(1).format(DATE_FMT);

            List<ActivityDto> recent = intervalsService.getActivities(from, to);
            if (recent.isEmpty()) {
                log.info("Keine neuen Rad-Aktivitäten in den letzten 2 Tagen.");
                return;
            }

            Set<String> skip = new HashSet<>(repository.findAllActivityIds());
            skip.addAll(jobService.runningActivityIds());

            List<ActivityDto> toAnalyze = recent.stream()
                    .filter(a -> !skip.contains(a.id()))
                    .toList();

            if (toAnalyze.isEmpty()) {
                log.info("Alle {} aktuellen Aktivitäten bereits analysiert oder in Arbeit.", recent.size());
                return;
            }

            log.info("{} neue Aktivität(en) gefunden – sende KI-Aufträge.", toAnalyze.size());
            for (ActivityDto activity : toAnalyze) {
                AnalysisJob job = jobService.start(activity.id(), activity);
                if (job.getStatus() == AnalysisJobStatus.FAILED) {
                    log.error("Analyse von '{}' ({}) nicht gestartet: {}", activity.name(), activity.id(), job.getError());
                }
            }
        } catch (Exception e) {
            log.error("Scheduler-Fehler beim Prüfen auf neue Aktivitäten: {}", e.getMessage(), e);
        }
    }

    /** Jobs ohne Antwort abbrechen, damit der naechste Lauf sie neu startet. */
    @Scheduled(fixedDelay = 60 * 1000, initialDelay = 60 * 1000)
    public void expireStaleJobs() {
        try {
            jobService.expireStaleJobs();
        } catch (RuntimeException e) {
            log.error("Fehler beim Aufraeumen haengender Analyse-Jobs: {}", e.getMessage(), e);
        }
    }
}
