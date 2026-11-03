package com.cycling.recommendation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Startet den ganzen Service ohne Broker und ohne intervals-service. */
@SpringBootTest(properties = {
        "mqtt.broker-url=tcp://localhost:1",
        "mqtt.client-id=recommendation-service-test",
        "intervals-service.base-url=http://localhost:1",
        "spring.main.keep-alive=false"
})
class RecommendationServiceApplicationTest {

    @Autowired
    private DailyRecommendationJob job;

    @Autowired
    private RecommendationEngine engine;

    @Test
    void startsWithJobAndEngine() {
        assertThat(job).isNotNull();
        assertThat(engine).isNotNull();
    }

    @Test
    void parsesTimeAndZoneOrFailsClearly() {
        assertThat(RecommendationConfig.parseTime("k", " 09:00 ")).isEqualTo(LocalTime.of(9, 0));
        assertThat(RecommendationConfig.parseZone("Europe/Berlin")).isEqualTo(ZoneId.of("Europe/Berlin"));
        assertThatThrownBy(() -> RecommendationConfig.parseTime("recommendation.daily.time", "9 Uhr"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("recommendation.daily.time");
        assertThatThrownBy(() -> RecommendationConfig.parseZone("Mars/Base"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("recommendation.zone");
    }
}
