package com.cycling.report.analysis;

import com.cycling.report.api.ZoneDistributionDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Gemini soll wissen, ob eine Fahrt indoor war und mit welcher FTP sie bewertet wird. */
class AnalysisPromptTest {

    private static ActivityAnalysis ride(String name, Boolean indoor, Integer ftp, Double intensity) {
        ActivityAnalysis a = new ActivityAnalysis();
        a.setActivityName(name);
        a.setActivityDate(LocalDateTime.of(2026, 10, 30, 18, 0));
        a.setIndoor(indoor);
        a.setDistanceKm(30.0);
        a.setDurationSeconds(3600);
        a.setAvgPowerWatts(200.0);
        a.setFtpUsed(ftp);
        a.setIntensityFactor(intensity);
        return a;
    }

    private static String prompt(ActivityAnalysis current, List<ActivityAnalysis> previous) {
        return AnalysisPrompt.build(new PreparedAnalysis(current, null,
                List.of(new ZoneDistributionDto("Z2", "Ausdauer", 1800, 50.0, "#0f0")), List.of(), previous,
                current.getFtpUsed(), 190));
    }

    @Test
    void indoorRideIsLabelledWithIndoorFtp() {
        String p = prompt(ride("Zwift", true, 235, 0.85), List.of());

        assertThat(p).contains("- Fahrtart: Indoor (Rolle / Smart-Trainer)");
        assertThat(p).contains("- FTP: 235 W (Indoor-FTP)");
        assertThat(p).contains("- Datum: 30.10.2026");
        assertThat(p).contains("- Dauer: 1:00:00 h");
    }

    @Test
    void outdoorRideIsLabelledWithOutdoorFtp() {
        String p = prompt(ride("Runde", false, 250, 0.8), List.of());

        assertThat(p).contains("- Fahrtart: Outdoor");
        assertThat(p).contains("- FTP: 250 W (Outdoor-FTP)");
        assertThat(p).contains("- Keine Vergleichsdaten vorhanden");
    }

    @Test
    void zonesAreListed() {
        assertThat(prompt(ride("Runde", false, 250, 0.8), List.of())).contains("- Z2 (Ausdauer): 50");
    }

    @Test
    void comparisonRidesShowTypeFtpAndIntensity() {
        ActivityAnalysis prevIndoor = ride("Zwift alt", true, 235, 0.88);
        ActivityAnalysis prevOld = ride("Uralt", null, null, 0.7);

        String p = prompt(ride("Runde", false, 250, 0.8), List.of(prevIndoor, prevOld));

        assertThat(p).contains("Zwift alt (30.10.2026) [Indoor, FTP 235 W, IF 0.88]");
        assertThat(p).contains("Uralt (30.10.2026) [Fahrtart unbekannt, IF 0.70]");
    }

    @Test
    void promptAsksToCompareRelativeToFtpAndForJson() {
        String p = prompt(ride("Runde", false, 250, 0.8), List.of());

        assertThat(p).contains("Vergleiche Leistungen relativ zur jeweiligen FTP (IF), nicht in absoluten Watt");
        assertThat(p).contains("Antworte NUR mit einem JSON-Objekt");
    }

    @Test
    void parsesPlainJson() {
        assertThat(AnalysisPrompt.parse("""
                {"score": 8, "strengths": "s", "anomalies": "a", "progress": "p", "summary": "z"}"""))
                .isEqualTo(new AiAssessment(8, "s", "a", "p", "z"));
    }

    @Test
    void parsesJsonInsideMarkdownBlock() {
        assertThat(AnalysisPrompt.parse("""
                ```json
                {"score": 6, "summary": "ok"}
                ```""")).isEqualTo(new AiAssessment(6, "", "", "", "ok"));
    }

    @Test
    void missingScoreFallsBackToDefault() {
        assertThat(AnalysisPrompt.parse("{\"summary\": \"ok\"}").score()).isEqualTo(AnalysisPrompt.DEFAULT_SCORE);
    }

    @Test
    void textWithoutJsonIsRejected() {
        assertThatThrownBy(() -> AnalysisPrompt.parse("Das ist kein JSON"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kein gueltiges JSON");
        assertThatThrownBy(() -> AnalysisPrompt.parse("[1, 2]"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
