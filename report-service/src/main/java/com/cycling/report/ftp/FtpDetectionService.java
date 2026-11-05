package com.cycling.report.ftp;

import com.cycling.messaging.MessagePublisher;
import com.cycling.messaging.Topics;
import com.cycling.messaging.contract.DetectFtpRequest;
import com.cycling.messaging.contract.DetectFtpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Steuert die manuell ausgeloeste FTP-Test-Erkennung: schickt einen {@link DetectFtpRequest} an den
 * ftp-service und speichert dessen {@link DetectFtpResponse} zur Aktivitaet.
 * <ul>
 *   <li>Ein Ergebnis pro Aktivitaet; ein neuer Lauf ueberschreibt es.</li>
 *   <li>Laeuft schon ein Lauf (PENDING, nicht abgelaufen), wird kein zweiter Auftrag gesendet.</li>
 *   <li>Ohne Antwort nach {@code timeout} gilt der Lauf als FAILED (geprueft beim Lesen, kein Scheduler).</li>
 *   <li>Antworten mit fremder {@code requestId} (alter Lauf) werden verworfen.</li>
 * </ul>
 */
public class FtpDetectionService {

    private static final Logger log = LoggerFactory.getLogger(FtpDetectionService.class);

    private final FtpDetectionRepository repository;
    private final MessagePublisher publisher;
    private final Clock clock;
    private final Duration timeout;

    public FtpDetectionService(FtpDetectionRepository repository, MessagePublisher publisher, Clock clock,
                               Duration timeout) {
        this.repository = repository;
        this.publisher = publisher;
        this.clock = clock;
        this.timeout = timeout;
    }

    /** Startet einen Lauf oder liefert den laufenden. Sendefehler landen als FAILED, nicht als Exception. */
    public FtpDetection start(String activityId) {
        Optional<FtpDetection> existing = find(activityId);
        if (existing.isPresent() && existing.get().getStatus() == FtpDetectionStatus.PENDING) {
            log.info("FTP-Erkennung fuer {} laeuft bereits ({})", activityId, existing.get().getRequestId());
            return existing.get();
        }

        String requestId = UUID.randomUUID().toString();
        FtpDetection detection = existing.orElseGet(() -> new FtpDetection(activityId, requestId, now()));
        detection.restart(requestId, now());
        // Erst speichern, dann senden: eine schnelle Antwort muss den Lauf schon finden.
        repository.save(detection);
        try {
            publisher.publish(Topics.DETECT_FTP_REQUEST, new DetectFtpRequest(requestId, activityId));
            log.info("FTP-Erkennung {} fuer {} angefordert", requestId, activityId);
        } catch (RuntimeException e) {
            log.error("FTP-Erkennung fuer {} konnte nicht angefordert werden: {}", activityId, e.getMessage());
            detection.markFailed("Auftrag konnte nicht gesendet werden: " + e.getMessage(), now());
            repository.save(detection);
        }
        return detection;
    }

    /** Ergebnis der Aktivitaet; ein abgelaufener PENDING-Lauf wird dabei auf FAILED gesetzt. */
    public Optional<FtpDetection> find(String activityId) {
        return repository.findById(activityId).map(this::expireIfStale);
    }

    /** Verarbeitet eine Antwort des ftp-service; idempotent bei doppelter Zustellung. */
    public void complete(DetectFtpResponse response) {
        FtpDetection detection = repository.findById(response.activityId()).orElse(null);
        if (detection == null) {
            log.warn("FTP-Ergebnis fuer unbekannte Aktivitaet {} verworfen", response.activityId());
            return;
        }
        if (!detection.getRequestId().equals(response.requestId())) {
            log.info("Veraltetes FTP-Ergebnis {} fuer {} verworfen (aktuell {})", response.requestId(),
                    response.activityId(), detection.getRequestId());
            return;
        }
        if (response.error() != null) {
            detection.markFailed(response.error(), now());
        } else if (response.isFtpDetected()) {
            detection.markDetected(response.ftp(), response.lthr(), response.maxHr(), response.avgPower20Min(),
                    response.windowStartSeconds(), now());
        } else {
            detection.markNotDetected(now());
        }
        repository.save(detection);
        log.info("FTP-Erkennung {} fuer {}: {}", response.requestId(), response.activityId(), detection.getStatus());
    }

    private FtpDetection expireIfStale(FtpDetection detection) {
        if (detection.getStatus() == FtpDetectionStatus.PENDING
                && detection.getRequestedAt().plus(timeout).isBefore(now())) {
            log.warn("FTP-Erkennung {} fuer {} ohne Antwort nach {} - abgebrochen", detection.getRequestId(),
                    detection.getActivityId(), timeout);
            detection.markFailed("Keine Antwort vom ftp-service innerhalb von " + timeout.toSeconds() + " s", now());
            repository.save(detection);
        }
        return detection;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
