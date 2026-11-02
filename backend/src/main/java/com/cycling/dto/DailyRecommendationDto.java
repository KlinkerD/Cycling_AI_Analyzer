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
        LocalDateTime computedAt,
        boolean textPending
) {
    /** So lange wartet das Frontend hoechstens auf den KI-Text, danach bleibt der Fallback stehen. */
    public static final java.time.Duration TEXT_WAIT = java.time.Duration.ofMinutes(2);

    public static DailyRecommendationDto from(DailyRecommendation r) {
        boolean textPending = r.getPendingTextRequestId() != null
                && r.getComputedAt() != null
                && r.getComputedAt().isAfter(LocalDateTime.now().minus(TEXT_WAIT));
        return new DailyRecommendationDto(
                r.getCategory().name(),
                r.getCategory().getLabel(),
                r.getCategory().getColor(),
                r.getText(),
                r.getForm(),
                r.getRampRate(),
                r.isRampCapped(),
                r.getRefDate(),
                r.getComputedAt(),
                textPending
        );
    }
}
