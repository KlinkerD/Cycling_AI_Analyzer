package com.cycling.report.analysis;

import com.cycling.types.ActivityId;

import com.cycling.report.api.ActivityDto;
import com.cycling.report.api.ReportDto;
import com.cycling.messaging.MessagePublisher;
import com.cycling.messaging.Topics;
import com.cycling.messaging.contract.AiRequest;
import com.cycling.messaging.contract.AiResult;
import com.cycling.report.ReportDelivery;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Steuert die asynchrone KI-Analyse: legt einen Job an, schickt den Prompt ({@link AnalysisPrompt}) per
 * MQTT an den ai-service und speichert die Analyse, sobald die Antwort zurueckkommt. Danach geht der fertige
 * Report in den Versand (Job-ID = Request-ID, damit eine doppelte Zustellung des Ergebnisses keinen
 * zweiten Versand ausloest).
 *
 * <p>Beim Ergebnis werden Streams/Zonen erneut geladen (statt sie im Speicher zu halten). Das kostet
 * ein paar zusaetzliche Aufrufe beim intervals-service (meist aus dessen Cache), dafuer uebersteht ein Job
 * einen Neustart des Services.
 */
public class AnalysisJobService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisJobService.class);
    /** Quelle beim ai-service: Auftraege auf cycling/ai/request/report, Antworten auf cycling/ai/result/report. */
    public static final String AI_SOURCE = "report";

    private final AnalysisService analysisService;
    private final AnalysisJobRepository jobRepository;
    private final MessagePublisher publisher;
    private final Consumer<ReportDelivery> delivery;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Duration timeout;

    public AnalysisJobService(AnalysisService analysisService, AnalysisJobRepository jobRepository,
                              MessagePublisher publisher, Consumer<ReportDelivery> delivery,
                              ObjectMapper objectMapper, Clock clock, Duration timeout) {
        this.analysisService = analysisService;
        this.jobRepository = jobRepository;
        this.publisher = publisher;
        this.delivery = delivery;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.timeout = timeout;
    }

    /**
     * Startet eine Analyse. Laeuft fuer die Aktivitaet schon ein Job, wird dieser zurueckgegeben.
     * Fehler beim Vorbereiten oder Senden landen als FAILED im Job, nicht als Exception.
     */
    public AnalysisJob start(ActivityId activityId, ActivityDto activity) {
        Optional<AnalysisJob> running =
                jobRepository.findFirstByActivityIdAndStatusOrderByCreatedAtDesc(activityId, AnalysisJobStatus.PENDING);
        if (running.isPresent()) {
            log.info("Analyse fuer {} laeuft bereits (Job {})", activityId, running.get().getId());
            return running.get();
        }

        AnalysisJob job = new AnalysisJob(UUID.randomUUID(), activityId, toJson(activity), now());
        try {
            PreparedAnalysis prepared = analysisService.prepare(activityId, activity);
            // Erst speichern, dann senden: ein schnelles Ergebnis muss den Job schon finden.
            jobRepository.save(job);
            publisher.publish(Topics.aiRequest(AI_SOURCE), toRequest(job.getId(), prepared));
            log.info("Analyse-Auftrag {} fuer '{}' gesendet", job.getId(), activity.name());
        } catch (RuntimeException e) {
            log.error("Analyse fuer {} konnte nicht gestartet werden: {}", activityId, e.getMessage());
            job.markFailed("Analyse konnte nicht gestartet werden: " + e.getMessage(), now());
            jobRepository.save(job);
        }
        return job;
    }

    /**
     * Verarbeitet eine Antwort des ai-service. Idempotent: doppelte Zustellungen fertiger Jobs werden
     * ignoriert. Eine Antwort, die erst nach dem Timeout kommt, wird trotzdem noch uebernommen.
     */
    public void complete(AiResult result) {
        AnalysisJob job = jobRepository.findById(result.requestId()).orElse(null);
        if (job == null) {
            log.warn("Ergebnis fuer unbekannten Analyse-Job {} verworfen", result.requestId());
            return;
        }
        if (job.getStatus() == AnalysisJobStatus.DONE) {
            log.debug("Ergebnis fuer Job {} bereits verarbeitet", job.getId());
            return;
        }
        if (!result.success()) {
            job.markFailed("KI-Analyse fehlgeschlagen: " + result.error(), now());
            jobRepository.save(job);
            return;
        }
        AiAssessment assessment;
        try {
            assessment = AnalysisPrompt.parse(result.text());
        } catch (IllegalArgumentException e) {
            log.error("Antwort fuer Job {} unbrauchbar: {}", job.getId(), e.getMessage());
            job.markFailed(e.getMessage(), now());
            jobRepository.save(job);
            return;
        }

        ReportDto report;
        try {
            ActivityDto activity = objectMapper.readValue(job.getActivityJson(), ActivityDto.class);
            report = analysisService.complete(analysisService.prepare(job.getActivityId(), activity), assessment);
        } catch (JsonProcessingException | RuntimeException e) {
            log.error("Ergebnis fuer Job {} konnte nicht gespeichert werden: {}", job.getId(), e.getMessage(), e);
            job.markFailed("Analyse konnte nicht gespeichert werden: " + e.getMessage(), now());
            jobRepository.save(job);
            return;
        }
        job.markDone(now());
        jobRepository.save(job);
        requestDelivery(job, report);
    }

    /** Fail-soft: die Analyse ist gespeichert; der Versand kann per Button nachgeholt werden. */
    private void requestDelivery(AnalysisJob job, ReportDto report) {
        try {
            delivery.accept(new ReportDelivery(job.getId(), job.getActivityId(), ReportContentMapper.toContent(report)));
        } catch (RuntimeException e) {
            log.warn("Versand fuer {} konnte nicht angestossen werden: {}", job.getActivityId(), e.getMessage());
        }
    }

    public Optional<AnalysisJob> find(UUID jobId) {
        return jobRepository.findById(jobId);
    }

    public Optional<AnalysisJob> latestFor(ActivityId activityId) {
        return jobRepository.findFirstByActivityIdOrderByCreatedAtDesc(activityId);
    }

    /** Aktivitaeten, fuer die gerade eine Analyse laeuft. */
    public Set<ActivityId> runningActivityIds() {
        return jobRepository.findByStatus(AnalysisJobStatus.PENDING).stream()
                .map(AnalysisJob::getActivityId)
                .collect(Collectors.toSet());
    }

    /** Jobs ohne Antwort nach {@code timeout} auf FAILED setzen, damit sie neu gestartet werden koennen. */
    public void expireStaleJobs() {
        LocalDateTime cutoff = now().minus(timeout);
        for (AnalysisJob job : jobRepository.findByStatusAndCreatedAtBefore(AnalysisJobStatus.PENDING, cutoff)) {
            log.warn("Analyse-Job {} ({}) ohne Antwort nach {} - abgebrochen", job.getId(), job.getActivityId(), timeout);
            job.markFailed("Keine Antwort vom ai-service innerhalb von " + timeout.toMinutes() + " Minuten", now());
            jobRepository.save(job);
        }
    }

    static AiRequest toRequest(UUID requestId, PreparedAnalysis prepared) {
        return new AiRequest(requestId, AnalysisPrompt.build(prepared), AnalysisPrompt.TEMPERATURE, true);
    }

    private String toJson(ActivityDto activity) {
        try {
            return objectMapper.writeValueAsString(activity);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Aktivitaet nicht serialisierbar: " + e.getMessage(), e);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
