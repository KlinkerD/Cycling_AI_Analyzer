package com.cycling.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Athleten-Schwellenwerte aus der intervals.icu API.
 * Endpoint: GET /api/v1/athlete/{id}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AthleteThresholdsDto(
        @JsonProperty("name") String name,
        @JsonProperty("ftp") Integer ftp,
        @JsonProperty("lthr") Integer lthr,
        @JsonProperty("max_hr") Integer maxHr,
        @JsonProperty("weight") Double weight
) {
    public int ftpOrDefault(int defaultFtp) {
        return (ftp != null && ftp > 0) ? ftp : defaultFtp;
    }

    public int maxHrOrDefault(int defaultMaxHr) {
        return (maxHr != null && maxHr > 0) ? maxHr : defaultMaxHr;
    }
}
