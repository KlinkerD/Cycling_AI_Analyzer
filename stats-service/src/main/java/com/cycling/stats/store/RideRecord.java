package com.cycling.stats.store;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Eine Fahrt mit den Werten, die die Statistik braucht. Summen werden erst bei der Abfrage gebildet.
 * {@code zonesLoaded = false} heisst: die Zonenzeiten konnten noch nicht geladen werden, der naechste
 * Abgleich versucht es erneut.
 */
@Entity
@Table(name = "stats_ride", indexes = @Index(name = "idx_stats_ride_date", columnList = "ride_date"))
public class RideRecord {

    @Id
    @Column(name = "activity_id", length = 64)
    private String activityId;

    /** Lokales Datum der Fahrt (Startzeit ohne Zone). */
    @Column(name = "ride_date", nullable = false)
    private LocalDate date;

    private String name;
    private String type;
    /** Meter */
    private double distance;
    /** Sekunden */
    private int movingTime;
    /** Sekunden */
    private int elapsedTime;
    /** Meter */
    private double elevationGain;
    /** TSS laut intervals.icu; 0, wenn nicht berechnet */
    private double trainingLoad;

    private boolean zonesLoaded;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "stats_ride_power_zone", joinColumns = @JoinColumn(name = "activity_id"))
    @OrderColumn(name = "position")
    private List<ZoneTime> powerZones = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "stats_ride_hr_zone", joinColumns = @JoinColumn(name = "activity_id"))
    @OrderColumn(name = "position")
    private List<ZoneTime> hrZones = new ArrayList<>();

    private Instant syncedAt;

    protected RideRecord() {
    }

    public RideRecord(String activityId) {
        this.activityId = activityId;
    }

    public String getActivityId() {
        return activityId;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public int getMovingTime() {
        return movingTime;
    }

    public void setMovingTime(int movingTime) {
        this.movingTime = movingTime;
    }

    public int getElapsedTime() {
        return elapsedTime;
    }

    public void setElapsedTime(int elapsedTime) {
        this.elapsedTime = elapsedTime;
    }

    public double getElevationGain() {
        return elevationGain;
    }

    public void setElevationGain(double elevationGain) {
        this.elevationGain = elevationGain;
    }

    public double getTrainingLoad() {
        return trainingLoad;
    }

    public void setTrainingLoad(double trainingLoad) {
        this.trainingLoad = trainingLoad;
    }

    public boolean isZonesLoaded() {
        return zonesLoaded;
    }

    public void setZonesLoaded(boolean zonesLoaded) {
        this.zonesLoaded = zonesLoaded;
    }

    public List<ZoneTime> getPowerZones() {
        return powerZones;
    }

    public void setPowerZones(List<ZoneTime> powerZones) {
        this.powerZones.clear();
        this.powerZones.addAll(powerZones);
    }

    public List<ZoneTime> getHrZones() {
        return hrZones;
    }

    public void setHrZones(List<ZoneTime> hrZones) {
        this.hrZones.clear();
        this.hrZones.addAll(hrZones);
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }

    public void setSyncedAt(Instant syncedAt) {
        this.syncedAt = syncedAt;
    }
}
