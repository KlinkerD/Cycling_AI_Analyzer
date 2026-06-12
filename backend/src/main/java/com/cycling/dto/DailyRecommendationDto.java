package com.cycling.dto;

import com.cycling.model.DailyRecommendation;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Tagesempfehlung fuer das Frontend (Overview-Karte unter dem Fitness-Chart).
 */
public record DailyRecommendationDto(
        String category,
        String categoryLabel,
        String color,
        String text,
        Double form,
        Double rampRate,
        boolean rampCapped,
        LocalDate refDate,
        LocalDateTime computedAt
) {
    public static DailyRecommendationDto from(DailyRecommendation r) {
        return new DailyRecommendationDto(
                r.getCategory().name(),
                r.getCategory().getLabel(),
                r.getCategory().getColor(),
                r.getText(),
                r.getForm(),
                r.getRampRate(),
                r.isRampCapped(),
                r.getRefDate(),
                r.getComputedAt()
        );
    }
}
