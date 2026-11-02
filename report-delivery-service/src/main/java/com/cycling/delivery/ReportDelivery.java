package com.cycling.delivery;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Versandstatus eines Reports in einem Kanal. Eine Zeile pro (activityId, channel). */
@Entity
@Table(name = "report_delivery",
        uniqueConstraints = @UniqueConstraint(columnNames = {"activityId", "channel"}))
public class ReportDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String activityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus status;

    private int attempts;

    /** Anzahl bereits erfolgreich gesendeter Teile (Discord: 1 = Text-Nachricht ist raus). */
    private int completedParts;

    @Column(length = 2000)
    private String lastError;

    /** Zeitpunkt des (Neu-)Einreihens; massgeblich fuer das Retry-Zeitfenster. */
    private LocalDateTime queuedAt;

    private LocalDateTime lastAttemptAt;

    /** Versandauftrag (MQTT), der diese Zeile zuletzt eingereiht hat; erkennt doppelte Zustellungen. */
    private String requestId;

    protected ReportDelivery() {
    }

    public ReportDelivery(String activityId, DeliveryChannel channel) {
        this.activityId = activityId;
        this.channel = channel;
        this.status = DeliveryStatus.PENDING;
    }

    /** Setzt die Zeile fuer einen kompletten Neuversand zurueck. */
    public void requeue(LocalDateTime now, String requestId) {
        this.requestId = requestId;
        status = DeliveryStatus.PENDING;
        attempts = 0;
        completedParts = 0;
        lastError = null;
        queuedAt = now;
    }

    public void markPartCompleted() {
        completedParts++;
    }

    public Long getId() { return id; }
    public String getActivityId() { return activityId; }
    public DeliveryChannel getChannel() { return channel; }
    public DeliveryStatus getStatus() { return status; }
    public void setStatus(DeliveryStatus status) { this.status = status; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public int getCompletedParts() { return completedParts; }
    public void setCompletedParts(int completedParts) { this.completedParts = completedParts; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public LocalDateTime getQueuedAt() { return queuedAt; }
    public void setQueuedAt(LocalDateTime queuedAt) { this.queuedAt = queuedAt; }
    public String getRequestId() { return requestId; }
    public LocalDateTime getLastAttemptAt() { return lastAttemptAt; }
    public void setLastAttemptAt(LocalDateTime lastAttemptAt) { this.lastAttemptAt = lastAttemptAt; }
}
