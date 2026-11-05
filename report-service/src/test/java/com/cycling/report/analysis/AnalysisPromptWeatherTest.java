package com.cycling.report.analysis;

import com.cycling.types.ActivityId;

import com.cycling.report.weather.RideWeatherPoint;
import com.cycling.report.weather.WeatherSample;
import com.cycling.report.weather.WeatherSummary;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Abschnitt WETTER im Prompt: nur mit Wetter, mit Wind relativ zur Fahrtrichtung. */
class AnalysisPromptWeatherTest {

    private static final Instant START = Instant.parse("2026-11-01T07:30:00Z");

    private static ActivityAnalysis ride(ActivityId id, String name) {
        ActivityAnalysis a = new ActivityAnalysis();
        a.setActivityId(id);
        a.setActivityName(name);
        a.setActivityDate(LocalDateTime.of(2026, 11, 1, 8, 30));
        a.setIndoor(false);
        a.setDistanceKm(30.0);
        a.setDurationSeconds(3600);
        a.setAvgPowerWatts(200.0);
        a.setFtpUsed(250);
        return a;
    }

    /** Fahrt nach Norden (Kurs 0), Wind aus Sueden = Rueckenwind; zweiter Punkt Wind aus Norden = Gegenwind. */
    private static final List<RideWeatherPoint> WEATHER = List.of(
            new RideWeatherPoint(new WeatherSample(0, 0.0, 48.1, 11.5, START, 0.0), 6.2, 12.0, 20.0, 180.0, 0.0),
            new RideWeatherPoint(new WeatherSample(10, 4.8, 48.15, 11.5, START.plusSeconds(600), 0.0),
                    7.8, 16.0, 30.0, 0.0, 1.2),
            new RideWeatherPoint(new WeatherSample(20, 9.6, 48.2, 11.5, START.plusSeconds(1200), null),
                    null, null, null, null, null));

    private static String prompt(List<RideWeatherPoint> weather, List<ActivityAnalysis> previous,
                                 Map<ActivityId, WeatherSummary> previousWeather) {
        return AnalysisPrompt.build(new PreparedAnalysis(ride(ActivityId.of("i1"), "Runde"), null, List.of(), List.of(), previous,
                250, 190, weather, previousWeather));
    }

    @Test
    void weatherSectionWithSummaryAndProgression() {
        String p = prompt(WEATHER, List.of(), Map.of());

        assertThat(p).contains("WETTER (Modelldaten Open-Meteo, keine Messung vor Ort):");
        assertThat(p).contains("- Temperatur: 6–8 °C (Ø 7 °C)");
        assertThat(p).contains("- Wind: Ø 14 km/h, Böen bis 30 km/h");
        assertThat(p).contains("- Niederschlag: 0.2 mm");
        assertThat(p).contains("- 0 | 0.0 | 6.2 | 12 | 20 | Rückenwind");
        assertThat(p).contains("- 10 | 4.8 | 7.8 | 16 | 30 | Gegenwind");
        assertThat(p).contains("Erfinde keinen Wettereinfluss");
    }

    @Test
    void pointWithoutTemperatureIsLeftOut() {
        assertThat(prompt(WEATHER, List.of(), Map.of())).doesNotContain("- 20 | 9.6");
    }

    @Test
    void noWeatherNoSection() {
        assertThat(prompt(List.of(), List.of(), Map.of())).doesNotContain("WETTER");
    }

    @Test
    void comparisonRidesShowTheirWeather() {
        ActivityAnalysis prev = ride(ActivityId.of("i0"), "Vorher");
        ActivityAnalysis prevWithout = ride(ActivityId.of("i-1"), "Ohne Wetter");

        String p = prompt(List.of(), List.of(prev, prevWithout),
                Map.of(ActivityId.of("i0"), new WeatherSummary(10, 14, 12.4, 18.2, 35.0, 0)));

        assertThat(p).containsPattern("Vorher \\(01\\.11\\.2026\\).*, Wetter: 12 °C, Wind 18 km/h");
        assertThat(p).doesNotContainPattern("Ohne Wetter.*Wetter:");
    }
}
