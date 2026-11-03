package com.cycling.fitness;

import com.cycling.messaging.contract.DeliveryMessage.Target;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FitnessTargetsTest {

    @Test
    void emptyConfigMeansNoTargets() {
        assertThat(FitnessTargets.parse("")).isEmpty();
        assertThat(FitnessTargets.parse(null)).isEmpty();
        assertThat(FitnessTargets.parse("  ")).isEmpty();
    }

    @Test
    void parsesChannelAndDestinationPairs() {
        assertThat(FitnessTargets.parse(" discord:fitness , MAIL:default "))
                .containsExactly(new Target("DISCORD", "fitness"), new Target("MAIL", "default"));
    }

    @Test
    void rejectsInvalidEntries() {
        assertThatThrownBy(() -> FitnessTargets.parse("DISCORD"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fitness.targets");
        assertThatThrownBy(() -> FitnessTargets.parse("DISCORD:"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> FitnessTargets.parse(":fitness"))
                .isInstanceOf(IllegalStateException.class);
    }
}
