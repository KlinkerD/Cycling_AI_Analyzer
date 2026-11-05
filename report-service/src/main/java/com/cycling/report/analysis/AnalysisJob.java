package com.cycling.report.analysis;

import com.cycling.types.ActivityId;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Ein KI-Analyseauftrag. Die ID ist zugleich die Korrelations-ID der MQTT-Nachrichten.
 * Das ActivityDto wird als JSON mitgespeichert, damit ein Ergebnis auch nach einem Neustart
 * des Backends noch verarbeitet werden kann.
 */
@Entity
@Table(name = "analysis_job", indexes = @Index(name = "idx_analysis_job_activity", columnList = "activityId"))
public class AnalysisJob {

    /** Als Text gespeichert (Spalte bleibt varchar). */
    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID id;

    @Column(nullable = false)
    private ActivityId activityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnalysisJobStatus status;

    @Column(columnDefinition = "TEXT")
    private String activityJson;

    @Column(columnDefinition = "TEXT")
    private String error;

    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;

    protected AnalysisJob() {
    }

    public AnalysisJob(UUID id, ActivityId activityId, String activityJson, LocalDateTime createdAt) {
        this.id = id;
        this.activityId = activityId;
        this.activityJson = activityJson;
        this.createdAt = createdAt;
        this.status = AnalysisJobStatus.PENDING;
    }

    public void markDone(LocalDateTime at) {
        status = AnalysisJobStatus.DONE;
        error = null;
        finishedAt = at;
    }

    public void markFailed(String reason, LocalDateTime at) {
        status = AnalysisJobStatus.FAILED;
        error = reason;
        finishedAt = at;
    }

    public UUID getId() {
        return id;
    }

    public ActivityId getActivityId() {
        return activityId;
    }

    public AnalysisJobStatus getStatus() {
        return status;
    }

    public String getActivityJson() {
        return activityJson;
    }

    public String getError() {
        return error;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }
}
