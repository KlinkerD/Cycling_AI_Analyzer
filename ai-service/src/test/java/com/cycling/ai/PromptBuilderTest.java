package com.cycling.ai;

import com.cycling.messaging.contract.AnalysisRequest;
import com.cycling.messaging.contract.RecommendationTextRequest;
import com.cycling.messaging.contract.WorkoutData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Gemini soll wissen, ob eine Fahrt indoor war und mit welcher FTP sie bewertet wird. */
class PromptBuilderTest {

    private final PromptBuilder prompts = new PromptBuilder();

    private static WorkoutData ride(String name, Boolean indoor, Integer ftp, Double intensity) {
        return new WorkoutData(name, "2026-10-30T18:00:00", indoor, 30.0, 3600, 200.0, null,
                null, null, null, null, null, ftp, intensity, null);
    }

    private String prompt(WorkoutData current, List<WorkoutData> previous) {
        return prompts.analysisPrompt(new AnalysisRequest("r1", current, List.of(), List.of(), previous,
                current.ftpUsed(), 190));
    }

    @Test
    void indoorRideIsLabelledWithIndoorFtp() {
        String p = prompt(ride("Zwift", true, 235, 0.85), List.of());

        assertThat(p).contains("- Fahrtart: Indoor (Rolle / Smart-Trainer)");
        assertThat(p).contains("- FTP: 235 W (Indoor-FTP)");
        assertThat(p).contains("- Datum: 30.10.2026");
    }

    @Test
    void outdoorRideIsLabelledWithOutdoorFtp() {
        String p = prompt(ride("Runde", false, 250, 0.8), List.of());

        assertThat(p).contains("- Fahrtart: Outdoor");
        assertThat(p).contains("- FTP: 250 W (Outdoor-FTP)");
    }

    @Test
    void comparisonRidesShowTypeFtpAndIntensity() {
        WorkoutData prevIndoor = ride("Zwift alt", true, 235, 0.88);
        WorkoutData prevOld = ride("Uralt", null, null, 0.7);

        String p = prompt(ride("Runde", false, 250, 0.8), List.of(prevIndoor, prevOld));

        assertThat(p).contains("Zwift alt (30.10.2026) [Indoor, FTP 235 W, IF 0.88]");
        assertThat(p).contains("Uralt (30.10.2026) [Fahrtart unbekannt, IF 0.70]");
    }

    @Test
    void promptAsksToCompareRelativeToFtp() {
        String p = prompt(ride("Runde", false, 250, 0.8), List.of());

        assertThat(p).contains("Vergleiche Leistungen relativ zur jeweiligen FTP (IF), nicht in absoluten Watt");
    }

    @Test
    void recommendationPromptKeepsCategoryAndMentionsRampCap() {
        String p = prompts.recommendationPrompt(
                new RecommendationTextRequest("r2", "Tempo / Sweet Spot", 12, 7.5, true, ""));

        assertThat(p).contains("darf NICHT geaendert werden: Tempo / Sweet Spot");
        assertThat(p).contains("Der Athlet baut schnell auf");
        assertThat(p).contains("- Keine Aktivitaeten erfasst.");
    }
}
