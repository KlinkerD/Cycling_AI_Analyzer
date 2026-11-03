package com.cycling.fitness;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class RampAssessmentTest {

    @ParameterizedTest
    @CsvSource({
            "12, TOO_FAST",
            "8.1, TOO_FAST",
            "8.0, FAST",
            "6.1, FAST",
            "-5.1, DECLINING",
            "-9, DECLINING"
    })
    void outsideTheHealthyRange(double ramp, RampAssessment expected) {
        assertThat(RampAssessment.of(ramp)).contains(expected);
    }

    @ParameterizedTest
    @ValueSource(doubles = {6.0, 3.2, 0, -5.0})
    void healthyRangeGivesNoHint(double ramp) {
        assertThat(RampAssessment.of(ramp)).isEmpty();
    }

    @Test
    void missingRampGivesNoHint() {
        assertThat(RampAssessment.of(null)).isEmpty();
    }
}
