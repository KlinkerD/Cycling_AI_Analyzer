package com.cycling.report.api;

import com.cycling.web.ApiNonNull;
public record ZoneDistributionDto(
        @ApiNonNull String zoneName,
        @ApiNonNull String zoneLabel,
        int seconds,
        double percentage,
        @ApiNonNull String color
) {}

