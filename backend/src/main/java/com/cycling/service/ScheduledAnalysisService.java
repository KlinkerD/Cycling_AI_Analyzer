package com.cycling.service;

import com.cycling.delivery.ReportDispatcher;
import com.cycling.delivery.Sleeper;
import com.cycling.dto.ActivityDto;
import com.cycling.dto.ReportDto;
import com.cycling.repository.ActivityAnalysisRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * Prüft alle 15 Minuten auf neue Aktivitäten der letzten 2 Tage.
 * Nur Aktivitäten OHNE vorhandenen Report werden analysiert.
 * Alte Aktivitäten ohne Report werden bewusst ignoriert.
 */
@Service
public class ScheduledAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledAnalysisService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final IntervalsService intervalsService;
    private final AnalysisService analysisService;
    private final ActivityAnalysisRepository repository;
    private final ReportDispatcher reportDispatcher;
    private final Sleeper sleeper;

    public ScheduledAnalysisService(IntervalsService intervalsService,
                                    AnalysisService analysisService,
                                    ActivityAnalysisRepository repository,
                                    ReportDispatcher reportDispatcher,
                                    Sleeper sleeper) {
        this.intervalsService = intervalsService;
        this.analysisService = analysisService;
        this.repository = repository;
        this.reportDispatcher = reportDispatcher;
        this.sleeper = sleeper;
    }

    /**
     * Läuft alle 15 Minuten.
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

            // Bereits analysierte IDs aus der DB
            Set<String> analyzedIds = Set.copyOf(repository.findAllActivityIds());

            List<ActivityDto> toAnalyze = recent.stream()
                    .filter(a -> !analyzedIds.contains(a.id()))
                    .toList();

            if (toAnalyze.isEmpty()) {
                log.info("Alle {} aktuellen Aktivitäten bereits analysiert.", recent.size());
                return;
            }

            log.info("{} neue Aktivität(en) gefunden – starte KI-Analyse.", toAnalyze.size());
            for (ActivityDto activity : toAnalyze) {
                try {
                    log.info("Analysiere: {} ({})", activity.name(), activity.id());
                    ReportDto report = analysisService.analyzeActivity(activity.id(), activity);
                    log.info("Analyse für '{}' erfolgreich abgeschlossen.", activity.name());
                    try {
                        reportDispatcher.dispatch(report);
                    } catch (RuntimeException e) {
                        log.warn("Report-Versand für '{}' konnte nicht angestossen werden: {}",
                                activity.name(), e.getMessage());
                    }
                    // Kurze Pause zwischen Analysen, um Gemini-Rate-Limits zu schonen
                    sleeper.sleep(Duration.ofSeconds(5));
                } catch (Exception e) {
                    log.error("Fehler bei der Analyse von '{}' ({}): {}", activity.name(), activity.id(), e.getMessage());
                    // Weitermachen mit nächster Aktivität
                }
            }
            log.info("=== Scheduled: Fertig. {} neue Report(s) erstellt ===", toAnalyze.size());

        } catch (Exception e) {
            log.error("Scheduler-Fehler beim Prüfen auf neue Aktivitäten: {}", e.getMessage(), e);
        }
    }
}
