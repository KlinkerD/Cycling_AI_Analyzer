package com.cycling.training.api;

import com.cycling.training.recommendation.Recommendation;
import com.cycling.web.ApiNonNull;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Tagesempfehlung fuer das Frontend (Overview-Karte unter dem Fitness-Chart).
 *
 * @param textPending true, solange der KI-Text noch erwartet wird (Frontend fragt dann erneut ab)
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

    public static DailyRecommendationDto from(Recommendation r) {
        return new DailyRecommendationDto(r.category(), r.categoryLabel(), r.color(), r.text(), r.form(),
                r.rampRate(), r.rampCapped(), r.refDate(), r.computedAt(), r.textPending());
    }
}
