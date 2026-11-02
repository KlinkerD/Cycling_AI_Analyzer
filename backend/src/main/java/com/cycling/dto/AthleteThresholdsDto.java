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
        /** Indoor-FTP aus intervals.icu ({@code indoor_ftp}); null, wenn nicht eingetragen. */
        @JsonProperty("indoorFtp") Integer indoorFtp,
        @JsonProperty("lthr") Integer lthr,
        @JsonProperty("maxHr") Integer maxHr,
        @JsonProperty("weight") Double weight
) {
    public int maxHrOrDefault(int defaultMaxHr) {
        return (maxHr != null && maxHr > 0) ? maxHr : defaultMaxHr;
    }
}
