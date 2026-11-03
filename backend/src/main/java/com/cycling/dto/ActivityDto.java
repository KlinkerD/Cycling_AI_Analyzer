package com.cycling.dto;

import com.cycling.config.ApiNonNull;
import com.cycling.intervals.api.IntervalsActivity;
import com.cycling.messaging.contract.ActivityDetected;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Eine Rad-Aktivitaet, wie sie das Frontend anzeigt und zum Analysieren zuruecksendet.
 * Die Werte kommen vom intervals-service ({@link IntervalsActivity}, dort sind die {@code icu_*}-Werte
 * von intervals.icu bereits bevorzugt) oder aus einer {@link ActivityDetected}-Meldung.
 *
 * <p>Wird auch als JSON im Analyse-Job gespeichert (camelCase).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ActivityDto {

    private String id;
    private String name;
    private String startDateLocal;
    private Double distance;
    private Integer movingTime;
    private Integer elapsedTime;
    private String type;
    private Double averageWatts;
    private Double normalizedPower;
    private Double maxWatts;
    private Double averageHeartrate;
    private Double maxHeartrate;
    private Double averageSpeed;
    private Double totalElevationGain;

    public static ActivityDto from(IntervalsActivity a) {
        return of(a.id(), a.name(), a.startDateLocal(), a.type(), a.distance(), a.movingTime(), a.elapsedTime(),
                a.averageWatts(), a.normalizedPower(), a.maxWatts(), a.averageHeartrate(), a.maxHeartrate(),
                a.averageSpeed(), a.totalElevationGain());
    }

    public static ActivityDto from(ActivityDetected a) {
        return of(a.activityId(), a.name(), a.startDateLocal(), a.type(), a.distance(), a.movingTime(),
                a.elapsedTime(), a.averageWatts(), a.normalizedPower(), a.maxWatts(), a.averageHeartrate(),
                a.maxHeartrate(), a.averageSpeed(), a.totalElevationGain());
    }

    private static ActivityDto of(String id, String name, String startDateLocal, String type, Double distance,
                                  Integer movingTime, Integer elapsedTime, Double averageWatts,
                                  Double normalizedPower, Double maxWatts, Double averageHeartrate,
                                  Double maxHeartrate, Double averageSpeed, Double totalElevationGain) {
        ActivityDto dto = new ActivityDto();
        dto.id = id;
        dto.name = name;
        dto.startDateLocal = startDateLocal;
        dto.type = type;
        dto.distance = distance;
        dto.movingTime = movingTime;
        dto.elapsedTime = elapsedTime;
        dto.averageWatts = averageWatts;
        dto.normalizedPower = normalizedPower;
        dto.maxWatts = maxWatts;
        dto.averageHeartrate = averageHeartrate;
        dto.maxHeartrate = maxHeartrate;
        dto.averageSpeed = averageSpeed;
        dto.totalElevationGain = totalElevationGain;
        return dto;
    }

    // ---- Setter (JSON vom Frontend bzw. aus dem Analyse-Job) ----
    public void setId(String v) { this.id = v; }
    public void setName(String v) { this.name = v; }
    public void setStartDateLocal(String v) { this.startDateLocal = v; }
    public void setDistance(Double v) { this.distance = v; }
    public void setMovingTime(Integer v) { this.movingTime = v; }
    public void setElapsedTime(Integer v) { this.elapsedTime = v; }
    public void setType(String v) { this.type = v; }
    public void setAverageWatts(Double v) { this.averageWatts = v; }
    public void setNormalizedPower(Double v) { this.normalizedPower = v; }
    public void setMaxWatts(Double v) { this.maxWatts = v; }
    public void setAverageHeartrate(Double v) { this.averageHeartrate = v; }
    public void setMaxHeartrate(Double v) { this.maxHeartrate = v; }
    public void setAverageSpeed(Double v) { this.averageSpeed = v; }
    public void setTotalElevationGain(Double v) { this.totalElevationGain = v; }

    // ---- Accessoren (auch fuer Serialisierung Richtung Frontend) ----
    @ApiNonNull @JsonProperty("id") public String id() { return id; }
    @JsonProperty("name") public String name() { return name; }
    @JsonProperty("startDateLocal") public String startDateLocal() { return startDateLocal; }
    /** Meter. */
    @JsonProperty("distance") public Double distance() { return distance; }
    /** Sekunden. */
    @JsonProperty("movingTime") public Integer movingTime() { return movingTime; }
    @JsonProperty("elapsedTime") public Integer elapsedTime() { return elapsedTime; }
    @JsonProperty("type") public String type() { return type; }
    @JsonProperty("averageWatts") public Double averageWatts() { return averageWatts; }
    @JsonProperty("maxWatts") public Double maxWatts() { return maxWatts; }
    /** Normalized Power laut intervals.icu, falls vorhanden. */
    @JsonProperty("normalizedPower") public Double normalizedPower() { return normalizedPower; }
    @JsonProperty("averageHeartrate") public Double averageHeartrate() { return averageHeartrate; }
    @JsonProperty("maxHeartrate") public Double maxHeartrate() { return maxHeartrate; }
    /** m/s. */
    @JsonProperty("averageSpeed") public Double averageSpeed() { return averageSpeed; }
    @JsonProperty("totalElevationGain") public Double totalElevationGain() { return totalElevationGain; }
}
