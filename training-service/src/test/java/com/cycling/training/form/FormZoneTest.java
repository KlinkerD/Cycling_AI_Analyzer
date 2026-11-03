package com.cycling.training.form;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class FormZoneTest {

    @ParameterizedTest
    @CsvSource({
            "40, TRANSITION",
            "25.0, TRANSITION",
            "24.9, FRESH",
            "5.0, FRESH",
            "4.9, GREY",
            "0, GREY",
            "-10.0, GREY",
            "-10.1, OPTIMAL",
            "-30.0, OPTIMAL",
            "-30.1, HIGH_RISK",
            "-60, HIGH_RISK"
    })
    void lowerBoundBelongsToTheZone(double form, FormZone expected) {
        assertThat(FormZone.of(form)).isEqualTo(expected);
    }
}
