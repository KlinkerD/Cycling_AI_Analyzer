package com.cycling.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "activity_analysis")
public class ActivityAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String activityId;

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

    public ActivityAnalysis() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getActivityId() {
        return activityId;
    }

    public void setActivityId(String activityId) {
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
}

