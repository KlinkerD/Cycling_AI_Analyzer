package com.cycling.delivery.report;

public record ZoneDistributionDto(
        String zoneName,
        String zoneLabel,
        int seconds,
        double percentage,
        String color
) {}

