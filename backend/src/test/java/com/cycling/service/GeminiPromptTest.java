package com.cycling.service;

import com.cycling.model.ActivityAnalysis;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Gemini soll wissen, ob eine Fahrt indoor war und mit welcher FTP sie bewertet wird. */
class GeminiPromptTest {

    private final GeminiService gemini = new GeminiService(null, null);

    private static ActivityAnalysis ride(String name, Boolean indoor, Integer ftp, Double intensity) {
        ActivityAnalysis a = new ActivityAnalysis();
        a.setActivityName(name);
        a.setActivityDate(LocalDateTime.of(2026, 10, 30, 18, 0));
        a.setDistanceKm(30.0);
        a.setDurationSeconds(3600);
        a.setAvgPowerWatts(200.0);
        a.setIndoor(indoor);
        a.setFtpUsed(ftp);
        a.setIntensityFactor(intensity);
        return a;
    }

    private String prompt(ActivityAnalysis current, List<ActivityAnalysis> lastThree) {
        return gemini.buildPrompt(current, List.of(), List.of(), lastThree, current.getFtpUsed(), 190);
    }

    @Test
    void indoorRideIsLabelledWithIndoorFtp() {
        String p = prompt(ride("Zwift", true, 235, 0.85), List.of());

        assertThat(p).contains("- Fahrtart: Indoor (Rolle / Smart-Trainer)");
        assertThat(p).contains("- FTP: 235 W (Indoor-FTP)");
    }

    @Test
    void outdoorRideIsLabelledWithOutdoorFtp() {
        String p = prompt(ride("Runde", false, 250, 0.8), List.of());

        assertThat(p).contains("- Fahrtart: Outdoor");
        assertThat(p).contains("- FTP: 250 W (Outdoor-FTP)");
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
    void promptAsksToCompareRelativeToFtp() {
        String p = prompt(ride("Runde", false, 250, 0.8), List.of());

        assertThat(p).contains("Vergleiche Leistungen relativ zur jeweiligen FTP (IF), nicht in absoluten Watt");
    }
}
