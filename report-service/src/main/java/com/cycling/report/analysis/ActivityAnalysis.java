package com.cycling.report.analysis;

import com.cycling.types.ActivityId;

import com.cycling.web.ApiNonNull;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "activity_analysis")
public class ActivityAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    @ApiNonNull
    private ActivityId activityId;

    private String activityName;
    private LocalDateTime activityDate;
    private Double distanceKm;
    private Integer durationSeconds;
    private Double avgPowerWatts;
    private Double maxPowerWatts;
    private Double avgHeartRate;
    private Double maxHeartRate;
    private Double avgSpeedKmh;
    private Double elevationGainM;
    private Double avgCadenceRpm;

    /** Normalized Power (belastungsgewichteter Leistungsschnitt) in Watt. */
    private Double normalizedPowerWatts;
    /** Intensity Factor = NP / FTP (typ. 0.5 - 1.05). */
    private Double intensityFactor;
    /** Training Stress Score (1h an der FTP = 100). */
    private Double trainingLoad;

    /** Indoor-Fahrt (VirtualRide oder Trainer-Flag); null bei Analysen aus alter Version. */
    private Boolean indoor;
    /** FTP in Watt, mit der IF/TSS berechnet wurden; null bei Analysen aus alter Version. */
    private Integer ftpUsed;

    private Integer overallScore;

    @Column(columnDefinition = "TEXT")
    private String strengths;

    @Column(columnDefinition = "TEXT")
    private String anomalies;

    @Column(columnDefinition = "TEXT")
    private String progress;

    @Column(columnDefinition = "TEXT")
    private String summary;

    private LocalDateTime analyzedAt;

    /**
     * Mean-Maximal-Power-Kurve dieser Aktivitaet auf festem Bucket-Schema
     * (siehe {@code PowerCurveService.STANDARD_BUCKETS}).
     * Eager geladen, weil die Kurve immer mit der Aktivitaet zusammen verwendet wird
     * und die Listengroesse klein ist (~24 Punkte).
     */
    @OneToMany(mappedBy = "analysis", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("durationSeconds ASC")
    private List<PowerCurvePoint> powerCurve = new ArrayList<>();

    public ActivityAnalysis() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ActivityId getActivityId() {
        return activityId;
    }

    public void setActivityId(ActivityId activityId) {
        this.activityId = activityId;
    }

    public String getActivityName() {
        return activityName;
    }

    public void setActivityName(String activityName) {
        this.activityName = activityName;
    }

    public LocalDateTime getActivityDate() {
        return activityDate;
    }

    public void setActivityDate(LocalDateTime activityDate) {
        this.activityDate = activityDate;
    }

    public Double getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(Double distanceKm) {
        this.distanceKm = distanceKm;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public Double getAvgPowerWatts() {
        return avgPowerWatts;
    }

    public void setAvgPowerWatts(Double avgPowerWatts) {
        this.avgPowerWatts = avgPowerWatts;
    }

    public Double getMaxPowerWatts() {
        return maxPowerWatts;
    }

    public void setMaxPowerWatts(Double maxPowerWatts) {
        this.maxPowerWatts = maxPowerWatts;
    }

    public Double getAvgHeartRate() {
        return avgHeartRate;
    }

    public void setAvgHeartRate(Double avgHeartRate) {
        this.avgHeartRate = avgHeartRate;
    }

    public Double getMaxHeartRate() {
        return maxHeartRate;
    }

    public void setMaxHeartRate(Double maxHeartRate) {
        this.maxHeartRate = maxHeartRate;
    }

    public Double getAvgSpeedKmh() {
        return avgSpeedKmh;
    }

    public void setAvgSpeedKmh(Double avgSpeedKmh) {
        this.avgSpeedKmh = avgSpeedKmh;
    }

    public Double getElevationGainM() {
        return elevationGainM;
    }

    public void setElevationGainM(Double elevationGainM) {
        this.elevationGainM = elevationGainM;
    }

    public Double getAvgCadenceRpm() {
        return avgCadenceRpm;
    }

    public void setAvgCadenceRpm(Double avgCadenceRpm) {
        this.avgCadenceRpm = avgCadenceRpm;
    }

    public Double getNormalizedPowerWatts() {
        return normalizedPowerWatts;
    }

    public void setNormalizedPowerWatts(Double normalizedPowerWatts) {
        this.normalizedPowerWatts = normalizedPowerWatts;
    }

    public Double getIntensityFactor() {
        return intensityFactor;
    }

    public void setIntensityFactor(Double intensityFactor) {
        this.intensityFactor = intensityFactor;
    }

    public Double getTrainingLoad() {
        return trainingLoad;
    }

    public void setTrainingLoad(Double trainingLoad) {
        this.trainingLoad = trainingLoad;
    }

    public Boolean getIndoor() {
        return indoor;
    }

    public void setIndoor(Boolean indoor) {
        this.indoor = indoor;
    }

    public Integer getFtpUsed() {
        return ftpUsed;
    }

    public void setFtpUsed(Integer ftpUsed) {
        this.ftpUsed = ftpUsed;
    }

    public Integer getOverallScore() {
        return overallScore;
    }

    public void setOverallScore(Integer overallScore) {
        this.overallScore = overallScore;
    }

    public String getStrengths() {
        return strengths;
    }

    public void setStrengths(String strengths) {
        this.strengths = strengths;
    }

    public String getAnomalies() {
        return anomalies;
    }

    public void setAnomalies(String anomalies) {
        this.anomalies = anomalies;
    }

    public String getProgress() {
        return progress;
    }

    public void setProgress(String progress) {
        this.progress = progress;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public LocalDateTime getAnalyzedAt() {
        return analyzedAt;
    }

    public void setAnalyzedAt(LocalDateTime analyzedAt) {
        this.analyzedAt = analyzedAt;
    }

    public List<PowerCurvePoint> getPowerCurve() {
        return powerCurve;
    }

    /**
     * Ersetzt die Power-Curve-Punkte und sorgt fuer korrekte Verknuepfung der Eltern-Beziehung.
     * Die Liste wird in-place mutiert, damit Hibernate die {@code orphanRemoval}-Logik anwendet.
     */
    public void replacePowerCurve(List<PowerCurvePoint> points) {
        this.powerCurve.clear();
        if (points != null) {
            for (PowerCurvePoint p : points) {
                p.setAnalysis(this);
                this.powerCurve.add(p);
            }
        }
    }
}

