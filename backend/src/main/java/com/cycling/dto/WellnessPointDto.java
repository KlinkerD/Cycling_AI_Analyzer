package com.cycling.dto;

import com.cycling.config.ApiNonNull;
/**
 * Ein Tagespunkt aus der intervals.icu Wellness-API
 * (CTL = Fitness, ATL = Ermuedung, Form = CTL - ATL).
 */
public record WellnessPointDto(
        @ApiNonNull String date,
        Double ctl,
        Double atl,
        Double form
) {}

