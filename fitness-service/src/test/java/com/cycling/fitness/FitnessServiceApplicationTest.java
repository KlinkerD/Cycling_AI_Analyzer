package com.cycling.fitness;

import com.cycling.messaging.contract.DeliveryMessage.Target;
import com.cycling.messaging.contract.WellnessUpdated;
import com.cycling.messaging.contract.WellnessUpdated.Values;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Startet den ganzen Service ohne Broker (Verbindungsaufbau laeuft im Hintergrund weiter). */
@SpringBootTest(properties = {
        "mqtt.broker-url=tcp://localhost:1",
        "mqtt.client-id=fitness-service-test",
        "spring.main.keep-alive=false"
})
class FitnessServiceApplicationTest {

    @Autowired
    private FitnessMessageBuilder messageBuilder;

    @Autowired
    private FitnessSummaryJob job;

    @Test
    void startsWithDefaultTargetDiscordFitness() {
        var message = messageBuilder.build(
                new WellnessUpdated(new Values("2026-11-03", 50.0, 55.0, -5.0, 3.0), null));

        assertThat(job).isNotNull();
        assertThat(message.targets()).containsExactly(new Target("DISCORD", "fitness"));
    }

    @Test
    void parsesTimeAndZoneOrFailsClearly() {
        assertThat(FitnessConfig.parseTime(" 21:00 ")).isEqualTo(LocalTime.of(21, 0));
        assertThat(FitnessConfig.parseZone("Europe/Berlin")).isEqualTo(ZoneId.of("Europe/Berlin"));
        assertThatThrownBy(() -> FitnessConfig.parseTime("21 Uhr"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("fitness.summary.time");
        assertThatThrownBy(() -> FitnessConfig.parseZone("Mars/Base"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("fitness.summary.zone");
    }
}
