package com.cycling.training.recommendation;

import com.cycling.intervals.api.IntervalsActivity;
import com.cycling.intervals.api.IntervalsWellnessPoint;
import com.cycling.training.form.FormZone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationCalculatorTest {

    private static IntervalsWellnessPoint day(String date, Double ctl, Double form) {
        return day(date, ctl, form, null);
    }

    private static IntervalsWellnessPoint day(String date, Double ctl, Double form, Double rampRate) {
        return new IntervalsWellnessPoint(date, ctl, ctl == null || form == null ? null : ctl - form, form, rampRate);
    }

    /** Dieselbe Skala wie die Form-Zonen der Fitness-Anzeige; Grenzen gehoeren zur unteren Zone. */
    @ParameterizedTest
    @CsvSource({
            "30, TRANSITION, HARD_INTERVALS",
            "25, TRANSITION, HARD_INTERVALS",
            "10, FRESH, HARD_INTERVALS",
            "5, FRESH, HARD_INTERVALS",
            "0, GREY, TEMPO",
            "-10, GREY, TEMPO",
            "-12, OPTIMAL, ENDURANCE",
            "-28, OPTIMAL, ENDURANCE",
            "-30, OPTIMAL, ENDURANCE",
            "-31, HIGH_RISK, REST"
    })
    void categoryFollowsFormZone(double form, FormZone zone, RecommendationCategory category) {
        Assessment a = RecommendationCalculator.assess(List.of(day("2026-11-03", 60.0, form, 0.0)));

        assertThat(a.formZone()).isEqualTo(zone);
        assertThat(a.category()).isEqualTo(category);
    }

    @Test
    void roundsFormAndKeepsReferenceDay() {
        Assessment a = RecommendationCalculator.assess(List.of(day("2026-11-03", 60.0, 10.4)));

        assertThat(a.form()).isEqualTo(10.0);
        assertThat(a.rampRate()).isEqualTo(0.0); // einziger Punkt ist seine eigene Referenz
        assertThat(a.refDate()).isEqualTo(LocalDate.of(2026, 11, 3));
    }

    @Test
    void rampFromIntervalsIsPreferredOverOwnCalculation() {
        Assessment a = RecommendationCalculator.assess(List.of(
                day("2026-10-27", 50.0, 0.0),
                day("2026-11-03", 57.5, 0.0, 3.24)));

        assertThat(a.rampRate()).isEqualTo(3.2);
        assertThat(a.rampCapped()).isFalse();
    }

    @Test
    void fastRampMakesCategoryOneStepSofter() {
        Assessment a = RecommendationCalculator.assess(List.of(
                day("2026-10-27", 50.0, 0.0),
                day("2026-11-03", 57.5, 20.0)));

        assertThat(a.rampRate()).isEqualTo(7.5);
        assertThat(a.rampCapped()).isTrue();
        assertThat(a.category()).isEqualTo(RecommendationCategory.TEMPO);
        assertThat(RecommendationCalculator.fallbackText(a)).contains("zurückhaltender");
    }

    @Test
    void rampOfSixIsStillHealthy() {
        Assessment a = RecommendationCalculator.assess(List.of(day("2026-11-03", 60.0, -12.0, 6.0)));

        assertThat(a.rampCapped()).isFalse();
        assertThat(a.category()).isEqualTo(RecommendationCategory.ENDURANCE);
    }

    @Test
    void cappedEnduranceBecomesEasy() {
        Assessment a = RecommendationCalculator.assess(List.of(day("2026-11-03", 60.0, -12.0, 9.0)));

        assertThat(a.category()).isEqualTo(RecommendationCategory.EASY);
    }

    @Test
    void restIsNeverCapped() {
        Assessment a = RecommendationCalculator.assess(List.of(day("2026-11-03", 60.0, -35.0, 10.0)));

        assertThat(a.category()).isEqualTo(RecommendationCategory.REST);
        assertThat(a.rampCapped()).isFalse();
    }

    @Test
    void usesLatestPointWithCtl() {
        Assessment a = RecommendationCalculator.assess(List.of(
                day("2026-11-02", 60.0, -12.0),
                day("2026-11-03", null, null)));

        assertThat(a.category()).isEqualTo(RecommendationCategory.ENDURANCE);
        assertThat(a.refDate()).isEqualTo(LocalDate.of(2026, 11, 2));
    }

    @Test
    void noDataIsInsufficient() {
        Assessment a = RecommendationCalculator.assess(List.of());

        assertThat(a.category()).isEqualTo(RecommendationCategory.INSUFFICIENT_DATA);
        assertThat(a.formZone()).isNull();
    }

    @Test
    void summaryListsNewestFirstWithLoadIntensityAndKm() {
        String summary = RecommendationCalculator.last7DaysSummary(List.of(
                ride("Morgenrunde", "2026-11-01T08:30:00", 85.0, 0.784, 42300.0),
                ride("Feierabend", "2026-11-02T18:00:00", null, null, null)));

        assertThat(summary).isEqualTo("- 02.11. Feierabend\n- 01.11. Morgenrunde, 85 TSS, IF 0,78, 42 km\n");
    }

    private static IntervalsActivity ride(String name, String start, Double load, Double intensity, Double distance) {
        return new IntervalsActivity("i", name, start, "Ride", distance, null, null, null, null, null, null, null,
                null, null, load, intensity, null);
    }
}
