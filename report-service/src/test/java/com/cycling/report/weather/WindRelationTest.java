package com.cycling.report.weather;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class WindRelationTest {

    @ParameterizedTest(name = "Kurs {0}, Wind aus {1} -> {2}")
    @CsvSource({
            "0, 0, HEADWIND",
            "0, 45, HEADWIND",
            "0, 315, HEADWIND",
            "90, 60, HEADWIND",
            "0, 180, TAILWIND",
            "0, 135, TAILWIND",
            "350, 170, TAILWIND",
            "0, 90, CROSSWIND_RIGHT",
            "0, 270, CROSSWIND_LEFT",
            "180, 270, CROSSWIND_RIGHT",
            "350, 80, CROSSWIND_RIGHT"
    })
    void sectors(double course, double windFrom, WindRelation expected) {
        assertThat(WindRelation.of(course, windFrom)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(value = {"null, 90", "90, null"}, nullValues = "null")
    void missingDirectionIsNull(Double course, Double windFrom) {
        assertThat(WindRelation.of(course, windFrom)).isNull();
    }
}
