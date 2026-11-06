package com.cycling.intervals.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IntervalsThresholdsUpdateTest {

    @Test
    void plausibleValuesHaveNoProblems() {
        IntervalsThresholdsUpdate update = IntervalsThresholdsUpdate.thresholds(280, 265, 168, 186, 74.5);

        assertThat(update.problems()).isEmpty();
        assertThat(update.hasSportSettings()).isTrue();
        assertThat(update.changesHeartRate()).isTrue();
    }

    @Test
    void emptyUpdateIsRejected() {
        IntervalsThresholdsUpdate update = IntervalsThresholdsUpdate.thresholds(null, null, null, null, null);

        assertThat(update.isEmpty()).isTrue();
        assertThat(update.problems()).containsExactly("Keine Werte zum Speichern angegeben.");
    }

    @Test
    void outOfRangeValuesAreNamed() {
        IntervalsThresholdsUpdate update = IntervalsThresholdsUpdate.thresholds(20, 900, 50, 300, 10.0);

        assertThat(update.problems()).containsExactly(
                "FTP outdoor muss zwischen 50 und 700 W liegen.",
                "FTP indoor muss zwischen 50 und 700 W liegen.",
                "LTHR muss zwischen 80 und 230 bpm liegen.",
                "HFmax muss zwischen 100 und 240 bpm liegen.",
                "Gewicht muss zwischen 30 und 200 kg liegen.");
    }

    @Test
    void lthrMustBeBelowMaxHr() {
        assertThat(IntervalsThresholdsUpdate.thresholds(null, null, 190, 185, null).problems())
                .containsExactly("LTHR muss unter HFmax liegen.");
    }

    private static IntervalsThresholdsUpdate zones(List<Integer> power, List<Integer> hr, Integer ssMin, Integer ssMax) {
        return new IntervalsThresholdsUpdate(null, null, null, null, null, power, hr, ssMin, ssMax);
    }

    @Test
    void plausibleZonesHaveNoProblems() {
        IntervalsThresholdsUpdate update = zones(List.of(55, 75, 90, 105, 120, 150), List.of(82, 89, 94, 99, 102, 106),
                84, 97);

        assertThat(update.problems()).isEmpty();
        assertThat(update.hasSportSettings()).isTrue();
    }

    @Test
    void zoneLimitsMustRise() {
        assertThat(zones(List.of(55, 75, 75, 105), null, null, null).problems())
                .containsExactly("Leistungszonen: Grenze von Z3 muss über der von Z2 liegen.");
        assertThat(zones(null, List.of(85, 80), null, null).problems())
                .containsExactly("HF-Zonen: Grenze von Z2 muss über der von Z1 liegen.");
    }

    @Test
    void zoneLimitsOutOfRangeOrMissingAreNamed() {
        assertThat(zones(List.of(0, 75), null, null, null).problems())
                .containsExactly("Leistungszonen: Grenze von Z1 muss zwischen 1 und 500 % FTP liegen.");
        assertThat(zones(null, java.util.Arrays.asList(85, null), null, null).problems())
                .containsExactly("HF-Zonen: Grenze von Z2 muss zwischen 30 und 150 % LTHR liegen.");
        assertThat(zones(List.of(), null, null, null).problems())
                .containsExactly("Leistungszonen: keine Grenzen angegeben.");
    }

    @Test
    void sweetSpotNeedsBothLimitsInOrder() {
        assertThat(zones(null, null, 84, null).problems())
                .containsExactly("Sweet Spot braucht Unter- und Obergrenze.");
        assertThat(zones(null, null, 97, 84).problems())
                .containsExactly("Sweet Spot: Untergrenze muss unter der Obergrenze liegen.");
        assertThat(zones(null, null, 20, 97).problems())
                .containsExactly("Sweet Spot (von) muss zwischen 50 und 150 % FTP liegen.");
    }

    @Test
    void ownHrZonesStopIntervalsFromRecalculating() {
        IntervalsThresholdsUpdate withZones = new IntervalsThresholdsUpdate(null, null, 172, null, null,
                null, List.of(82, 89), null, null);

        assertThat(withZones.changesHeartRate()).isTrue();
        assertThat(withZones.recalcHrZones()).isFalse();
        assertThat(IntervalsThresholdsUpdate.thresholds(null, null, 172, null, null).recalcHrZones()).isTrue();
    }

    @Test
    void weightOnlyTouchesNoSportSettings() {
        IntervalsThresholdsUpdate update = IntervalsThresholdsUpdate.thresholds(null, null, null, null, 72.0);

        assertThat(update.hasSportSettings()).isFalse();
        assertThat(update.changesHeartRate()).isFalse();
        assertThat(update.problems()).isEmpty();
    }
}
