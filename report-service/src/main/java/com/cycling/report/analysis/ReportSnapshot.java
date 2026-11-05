package com.cycling.report.analysis;

import com.cycling.types.ActivityId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Gespeicherte {@link ReportData} einer Fahrt als JSON (eine Zeile pro Aktivitaet). Die Streams einer langen Fahrt
 * sind einige hundert KB gross, deshalb eine eigene Tabelle: die Analyse-Liste und der Trend laden sie nicht mit.
 */
@Entity
@Table(name = "report_snapshot")
public class ReportSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private ActivityId activityId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String dataJson;

    @Column(nullable = false)
    private Instant fetchedAt;

    protected ReportSnapshot() {
    }

    public ReportSnapshot(ActivityId activityId, String dataJson, Instant fetchedAt) {
        this.activityId = activityId;
        this.dataJson = dataJson;
        this.fetchedAt = fetchedAt;
    }

    public ActivityId getActivityId() {
        return activityId;
    }

    public String getDataJson() {
        return dataJson;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    void update(String dataJson, Instant fetchedAt) {
        this.dataJson = dataJson;
        this.fetchedAt = fetchedAt;
    }
}
