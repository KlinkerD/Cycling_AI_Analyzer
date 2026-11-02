package com.cycling.dto;

import com.cycling.config.ApiNonNull;
public record ZoneDistributionDto(
        @ApiNonNull String zoneName,
        @ApiNonNull String zoneLabel,
        int seconds,
        double percentage,
        @ApiNonNull String color
) {}

