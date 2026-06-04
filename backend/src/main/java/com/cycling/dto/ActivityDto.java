package com.cycling.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO für Aktivitäten aus der intervals.icu API.
 * API-Doku: https://intervals.icu/api/v1/docs
 * Auth: Basic Auth – Username: "API", Password: dein API-Key
 *
 * intervals.icu liefert sowohl die Strava-Originalfelder (z.B. "average_watts")
 * als auch die von intervals.icu neu berechneten Felder (z.B. "icu_average_watts").
 * Wir lesen beide ein und bevorzugen die "icu_*"-Werte, da diese auch dann gesetzt
 * sind, wenn die Aktivität nicht von Strava stammt (z.B. Garmin-Direkt-Upload).
 *
 * Für die Serialisierung Richtung Frontend werden ausschliesslich die zusammengefuehrten
 * camelCase-Properties (averageWatts, averageHeartrate, ...) exponiert.
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

    // Rohwerte aus intervals.icu – nur fuer das Einlesen, nicht fuer die Ausgabe
    @JsonIgnore private Double averageWattsRaw;
    @JsonIgnore private Double icuAverageWatts;
    @JsonIgnore private Double icuWeightedAvgWatts;
    @JsonIgnore private Double maxWattsRaw;
    @JsonIgnore private Double icuMaxWatts;
    @JsonIgnore private Double averageHeartrateRaw;
    @JsonIgnore private Double icuAverageHeartrate;
    @JsonIgnore private Double maxHeartrateRaw;
    @JsonIgnore private Double icuMaxHeartrate;
    @JsonIgnore private Double averageSpeedRaw;
    @JsonIgnore private Double icuAverageSpeed;
    @JsonIgnore private Double totalElevationGainRaw;
    @JsonIgnore private Double icuElevationGain;

    // ---- Setter mit JSON-Property: nur fuer Deserialisierung ----
    // intervals.icu liefert snake_case (z.B. "moving_time"), das Frontend postet das
    // serialisierte DTO mit camelCase-Keys (z.B. "movingTime"). Beides muss akzeptiert werden,
    // sonst landet beim Analyse-POST z.B. movingTime=null in der DB -> Dauer = 0:00 im Report.
    @JsonProperty("id") public void setId(String v) { this.id = v; }
    @JsonProperty("name") public void setName(String v) { this.name = v; }
    @JsonProperty("start_date_local") @JsonAlias("startDateLocal") public void setStartDateLocal(String v) { this.startDateLocal = v; }
    @JsonProperty("distance") public void setDistance(Double v) { this.distance = v; }
    @JsonProperty("moving_time") @JsonAlias("movingTime") public void setMovingTime(Integer v) { this.movingTime = v; }
    @JsonProperty("elapsed_time") @JsonAlias("elapsedTime") public void setElapsedTime(Integer v) { this.elapsedTime = v; }
    @JsonProperty("type") public void setType(String v) { this.type = v; }

    @JsonProperty("average_watts") @JsonAlias("averageWatts") public void setAverageWattsRaw(Double v) { this.averageWattsRaw = v; }
    @JsonProperty("icu_average_watts") public void setIcuAverageWatts(Double v) { this.icuAverageWatts = v; }
    @JsonProperty("icu_weighted_avg_watts") public void setIcuWeightedAvgWatts(Double v) { this.icuWeightedAvgWatts = v; }
    @JsonProperty("max_watts") @JsonAlias("maxWatts") public void setMaxWattsRaw(Double v) { this.maxWattsRaw = v; }
    @JsonProperty("icu_max_watts") public void setIcuMaxWatts(Double v) { this.icuMaxWatts = v; }

    @JsonProperty("average_heartrate") @JsonAlias("averageHeartrate") public void setAverageHeartrateRaw(Double v) { this.averageHeartrateRaw = v; }
    @JsonProperty("icu_average_heartrate") public void setIcuAverageHeartrate(Double v) { this.icuAverageHeartrate = v; }
    @JsonProperty("max_heartrate") @JsonAlias("maxHeartrate") public void setMaxHeartrateRaw(Double v) { this.maxHeartrateRaw = v; }
    @JsonProperty("icu_max_heartrate") public void setIcuMaxHeartrate(Double v) { this.icuMaxHeartrate = v; }

    @JsonProperty("average_speed") @JsonAlias("averageSpeed") public void setAverageSpeedRaw(Double v) { this.averageSpeedRaw = v; }
    @JsonProperty("icu_average_speed") public void setIcuAverageSpeed(Double v) { this.icuAverageSpeed = v; }

    @JsonProperty("total_elevation_gain") @JsonAlias("totalElevationGain") public void setTotalElevationGainRaw(Double v) { this.totalElevationGainRaw = v; }
    @JsonProperty("icu_elevation_gain") public void setIcuElevationGain(Double v) { this.icuElevationGain = v; }

    // ---- Accessoren (auch fuer Serialisierung Richtung Frontend) ----
    @JsonProperty("id") public String id() { return id; }
    @JsonProperty("name") public String name() { return name; }
    @JsonProperty("startDateLocal") public String startDateLocal() { return startDateLocal; }
    @JsonProperty("distance") public Double distance() { return distance; }
    @JsonProperty("movingTime") public Integer movingTime() { return movingTime; }
    @JsonProperty("elapsedTime") public Integer elapsedTime() { return elapsedTime; }
    @JsonProperty("type") public String type() { return type; }

    @JsonProperty("averageWatts")
    public Double averageWatts() {
        return firstNonNull(icuAverageWatts, icuWeightedAvgWatts, averageWattsRaw);
    }

    @JsonProperty("maxWatts")
    public Double maxWatts() {
        return firstNonNull(icuMaxWatts, maxWattsRaw);
    }

    @JsonProperty("averageHeartrate")
    public Double averageHeartrate() {
        return firstNonNull(icuAverageHeartrate, averageHeartrateRaw);
    }

    @JsonProperty("maxHeartrate")
    public Double maxHeartrate() {
        return firstNonNull(icuMaxHeartrate, maxHeartrateRaw);
    }

    @JsonProperty("averageSpeed")
    public Double averageSpeed() {
        return firstNonNull(icuAverageSpeed, averageSpeedRaw);
    }

    @JsonProperty("totalElevationGain")
    public Double totalElevationGain() {
        return firstNonNull(icuElevationGain, totalElevationGainRaw);
    }

    private static Double firstNonNull(Double... values) {
        for (Double v : values) {
            if (v != null) return v;
        }
        return null;
    }
}
