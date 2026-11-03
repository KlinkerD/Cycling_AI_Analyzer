package com.cycling.training.recommendation;

import com.cycling.training.form.FormZone;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationPromptTest {

    private static Assessment assessment(boolean capped) {
        return new Assessment(RecommendationCategory.TEMPO, FormZone.GREY, -4.0, 7.5, capped, LocalDate.of(2026, 11, 3));
    }

    @Test
    void keepsCategoryAndNamesFormZone() {
        String p = RecommendationPrompt.build(assessment(false), "- 02.11. Runde, 70 TSS\n");

        assertThat(p).contains("darf NICHT geaendert werden: Tempo / Sweet Spot");
        assertThat(p).contains("Form (TSB): -4 – Zone \"Grauzone\"");
        assertThat(p).contains("Ramp Rate (CTL-Aufbau pro Woche): 7,5");
        assertThat(p).contains("- 02.11. Runde, 70 TSS");
        assertThat(p).doesNotContain("baut schnell auf");
    }

    @Test
    void mentionsRampCapAndMissingRides() {
        String p = RecommendationPrompt.build(assessment(true), "");

        assertThat(p).contains("Der Athlet baut schnell auf");
        assertThat(p).contains("- Keine Aktivitaeten erfasst.");
    }
}
