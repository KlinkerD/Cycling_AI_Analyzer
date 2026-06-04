package com.cycling.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

/**
 * Ein Punkt der "Mean Maximal Power"-Kurve einer Aktivitaet.
 * Schluessel = Dauer in Sekunden, Wert = beste durchschnittliche Leistung in Watt
 * (auf einem festen, log-skalierten Bucket-Schema, siehe PowerCurveService).
 */
@Entity
@Table(name = "power_curve_point",
        indexes = @Index(name = "idx_pcp_analysis", columnList = "activity_analysis_id"))
public class PowerCurvePoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "activity_analysis_id", nullable = false)
    @JsonIgnore
    private ActivityAnalysis analysis;

    @Column(nullable = false)
    private int durationSeconds;

    /** Watt fuer diese Dauer; null = nicht verfuegbar (z.B. Aktivitaet kuerzer als Bucket). */
    private Integer watts;

    public PowerCurvePoint() {
    }

    public PowerCurvePoint(int durationSeconds, Integer watts) {
        this.durationSeconds = durationSeconds;
        this.watts = watts;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ActivityAnalysis getAnalysis() {
        return analysis;
    }

    public void setAnalysis(ActivityAnalysis analysis) {
        this.analysis = analysis;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public Integer getWatts() {
        return watts;
    }

    public void setWatts(Integer watts) {
        this.watts = watts;
    }
}

