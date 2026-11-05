package com.cycling.report.ftp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Ergebnis der FTP-Test-Erkennung einer Fahrt. Genau eins pro Aktivitaet; ein neuer Lauf ueberschreibt
 * das alte Ergebnis. {@code requestId} gehoert zum aktuellen Lauf, Antworten mit anderer ID sind veraltet.
 */
@Entity
@Table(name = "ftp_detection")
public class FtpDetection {

    @Id
    private String activityId;

    @Column(nullable = false)
    private String requestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FtpDetectionStatus status;

    /** Watt, 20-Minuten-Mittel * 0,95 */
    private Integer ftp;
    /** bpm, mittlerer Puls im 20-Minuten-Block */
    private Integer lthr;
    /** bpm, hoechstes 5-Sekunden-Mittel der Fahrt */
    private Integer maxHr;
    /** Watt, mittlere Leistung im 20-Minuten-Block */
    private Integer avgPower20Min;
    /** Start des 20-Minuten-Blocks in Sekunden ab Fahrtbeginn */
    private Integer windowStartSeconds;

    @Column(columnDefinition = "TEXT")
    private String error;

    @Column(nullable = false)
    private LocalDateTime requestedAt;
    private LocalDateTime finishedAt;

    protected FtpDetection() {
    }

    public FtpDetection(String activityId, String requestId, LocalDateTime requestedAt) {
        this.activityId = activityId;
        restart(requestId, requestedAt);
    }

    /** Neuer Lauf: altes Ergebnis verwerfen. */
    public void restart(String requestId, LocalDateTime at) {
        this.requestId = requestId;
        this.status = FtpDetectionStatus.PENDING;
        this.requestedAt = at;
        clearValues();
        this.error = null;
        this.finishedAt = null;
    }

    public void markDetected(Integer ftp, Integer lthr, Integer maxHr, Integer avgPower20Min,
                             Integer windowStartSeconds, LocalDateTime at) {
        this.status = FtpDetectionStatus.DETECTED;
        this.ftp = ftp;
        this.lthr = lthr;
        this.maxHr = maxHr;
        this.avgPower20Min = avgPower20Min;
        this.windowStartSeconds = windowStartSeconds;
        this.error = null;
        this.finishedAt = at;
    }

    public void markNotDetected(LocalDateTime at) {
        this.status = FtpDetectionStatus.NOT_DETECTED;
        clearValues();
        this.error = null;
        this.finishedAt = at;
    }

    public void markFailed(String reason, LocalDateTime at) {
        this.status = FtpDetectionStatus.FAILED;
        clearValues();
        this.error = reason;
        this.finishedAt = at;
    }

    private void clearValues() {
        ftp = null;
        lthr = null;
        maxHr = null;
        avgPower20Min = null;
        windowStartSeconds = null;
    }

    public String getActivityId() {
        return activityId;
    }

    public String getRequestId() {
        return requestId;
    }

    public FtpDetectionStatus getStatus() {
        return status;
    }

    public Integer getFtp() {
        return ftp;
    }

    public Integer getLthr() {
        return lthr;
    }

    public Integer getMaxHr() {
        return maxHr;
    }

    public Integer getAvgPower20Min() {
        return avgPower20Min;
    }

    public Integer getWindowStartSeconds() {
        return windowStartSeconds;
    }

    public String getError() {
        return error;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }
}
