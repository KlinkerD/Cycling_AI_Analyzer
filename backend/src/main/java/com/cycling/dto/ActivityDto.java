package com.cycling.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO für Aktivitäten aus der intervals.icu API.
 * API-Doku: https://intervals.icu/api/v1/docs
 * Auth: Basic Auth – Username: "API", Password: dein API-Key
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ActivityDto(
        @JsonAlias("id") String id,
        @JsonAlias("name") String name,
        @JsonAlias("start_date_local") String startDateLocal,
        @JsonAlias("distance") Double distance,
        @JsonAlias("moving_time") Integer movingTime,
        @JsonAlias("elapsed_time") Integer elapsedTime,
        @JsonAlias("average_watts") Double averageWatts,
        @JsonAlias("max_watts") Double maxWatts,
        @JsonAlias("average_heartrate") Double averageHeartrate,
        @JsonAlias("max_heartrate") Double maxHeartrate,
        @JsonAlias("average_speed") Double averageSpeed,
        @JsonAlias("total_elevation_gain") Double totalElevationGain,
        @JsonAlias("type") String type
) {}

