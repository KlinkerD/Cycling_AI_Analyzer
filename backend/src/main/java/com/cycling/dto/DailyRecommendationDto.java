package com.cycling.dto;

import com.cycling.config.ApiNonNull;
import com.cycling.messaging.contract.RecommendationUpdated;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Tagesempfehlung fuer das Frontend (Overview-Karte unter dem Fitness-Chart). Berechnet wird sie im
 * recommendation-service, das backend reicht sie nur durch (siehe {@code RecommendationStore}).
 */
public record DailyRecommendationDto(
        @Schema(allowableValues = {"REST", "EASY", "ENDURANCE", "TEMPO", "HARD_INTERVALS", "INSUFFICIENT_DATA"})
        @ApiNonNull String category,
        @ApiNonNull String categoryLabel,
        @ApiNonNull String color,
        @ApiNonNull String text,
        Double form,
        Double rampRate,
        boolean rampCapped,
        LocalDate refDate,
        LocalDateTime computedAt,
        boolean textPending
) {
    /** So lange wartet das Frontend hoechstens auf den KI-Text, danach bleibt der Fallback stehen. */
    public static final java.time.Duration TEXT_WAIT = java.time.Duration.ofMinutes(2);

    /** @param textPending ob das Frontend noch auf den KI-Text warten soll (backend begrenzt auf {@link #TEXT_WAIT}) */
    public static DailyRecommendationDto from(RecommendationUpdated r, boolean textPending) {
        return new DailyRecommendationDto(
                r.category(),
                r.categoryLabel(),
                r.color(),
                r.text(),
                r.form(),
                r.rampRate(),
                r.rampCapped(),
                r.refDate(),
                r.computedAt(),
                textPending
        );
    }
}
