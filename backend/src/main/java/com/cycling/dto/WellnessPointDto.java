package com.cycling.dto;

/**
 * Ein Tagespunkt aus der intervals.icu Wellness-API
 * (CTL = Fitness, ATL = Ermuedung, Form = CTL - ATL).
 */
public record WellnessPointDto(
        String date,
        Double ctl,
        Double atl,
        Double form
) {}

