package com.cycling.training;

import com.cycling.messaging.contract.DeliveryMessage.Target;
import com.cycling.messaging.contract.WellnessUpdated;
import com.cycling.messaging.contract.WellnessUpdated.Values;
import com.cycling.training.fitness.FitnessMessageBuilder;
import com.cycling.training.fitness.FitnessSummaryJob;
import com.cycling.training.recommendation.DailyRecommendationJob;
import com.cycling.training.recommendation.RecommendationEngine;
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
        "mqtt.client-id=training-service-test",
        "intervals-service.base-url=http://localhost:1",
        "spring.main.keep-alive=false"
})
class TrainingServiceApplicationTest {

    @Autowired
    private DailyRecommendationJob recommendationJob;

    @Autowired
    private RecommendationEngine engine;

    @Autowired
    private FitnessMessageBuilder fitnessMessageBuilder;

    @Autowired
    private FitnessSummaryJob fitnessJob;

    @Test
    void startsWithFitnessAndRecommendationParts() {
        assertThat(recommendationJob).isNotNull();
        assertThat(engine).isNotNull();
        assertThat(fitnessJob).isNotNull();
    }

    @Test
    void fitnessSummaryGoesToDefaultTargetDiscordFitness() {
        var message = fitnessMessageBuilder.build(
                new WellnessUpdated(new Values("2026-11-03", 50.0, 55.0, -5.0, 3.0), null));

        assertThat(message.targets()).containsExactly(new Target("DISCORD", "fitness"));
    }

    @Test
    void parsesTimeAndZoneOrFailsClearly() {
        assertThat(TrainingConfig.parseTime("k", " 09:00 ")).isEqualTo(LocalTime.of(9, 0));
        assertThat(TrainingConfig.parseZone("Europe/Berlin")).isEqualTo(ZoneId.of("Europe/Berlin"));
        assertThatThrownBy(() -> TrainingConfig.parseTime("fitness.summary.time", "21 Uhr"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("fitness.summary.time");
        assertThatThrownBy(() -> TrainingConfig.parseZone("Mars/Base"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("training.zone");
    }
}
