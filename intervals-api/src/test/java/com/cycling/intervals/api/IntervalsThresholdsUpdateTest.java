package com.cycling.intervals.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IntervalsThresholdsUpdateTest {

    @Test
    void plausibleValuesHaveNoProblems() {
        IntervalsThresholdsUpdate update = new IntervalsThresholdsUpdate(280, 265, 168, 186, 74.5);

        assertThat(update.problems()).isEmpty();
        assertThat(update.hasSportSettings()).isTrue();
        assertThat(update.changesHeartRate()).isTrue();
    }

    @Test
    void emptyUpdateIsRejected() {
        IntervalsThresholdsUpdate update = new IntervalsThresholdsUpdate(null, null, null, null, null);

        assertThat(update.isEmpty()).isTrue();
        assertThat(update.problems()).containsExactly("Keine Werte zum Speichern angegeben.");
    }

    @Test
    void outOfRangeValuesAreNamed() {
        IntervalsThresholdsUpdate update = new IntervalsThresholdsUpdate(20, 900, 50, 300, 10.0);

        assertThat(update.problems()).containsExactly(
                "FTP outdoor muss zwischen 50 und 700 W liegen.",
                "FTP indoor muss zwischen 50 und 700 W liegen.",
                "LTHR muss zwischen 80 und 230 bpm liegen.",
                "HFmax muss zwischen 100 und 240 bpm liegen.",
                "Gewicht muss zwischen 30 und 200 kg liegen.");
    }

    @Test
    void lthrMustBeBelowMaxHr() {
        assertThat(new IntervalsThresholdsUpdate(null, null, 190, 185, null).problems())
                .containsExactly("LTHR muss unter HFmax liegen.");
    }

    @Test
    void weightOnlyTouchesNoSportSettings() {
        IntervalsThresholdsUpdate update = new IntervalsThresholdsUpdate(null, null, null, null, 72.0);

        assertThat(update.hasSportSettings()).isFalse();
        assertThat(update.changesHeartRate()).isFalse();
        assertThat(update.problems()).isEmpty();
    }
}
