package com.cycling.training;

import com.cycling.messaging.contract.DeliveryMessage.Target;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryTargetsTest {

    @Test
    void emptyConfigMeansNoTargets() {
        assertThat(DeliveryTargets.parse("k", "")).isEmpty();
        assertThat(DeliveryTargets.parse("k", null)).isEmpty();
        assertThat(DeliveryTargets.parse("k", "  ")).isEmpty();
    }

    @Test
    void parsesChannelAndDestinationPairs() {
        assertThat(DeliveryTargets.parse("k", " discord:fitness , MAIL:default "))
                .containsExactly(new Target("DISCORD", "fitness"), new Target("MAIL", "default"));
    }

    @Test
    void rejectsInvalidEntriesAndNamesTheSetting() {
        assertThatThrownBy(() -> DeliveryTargets.parse("fitness.targets", "DISCORD"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fitness.targets");
        assertThatThrownBy(() -> DeliveryTargets.parse("recommendation.targets", "DISCORD:"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("recommendation.targets");
        assertThatThrownBy(() -> DeliveryTargets.parse("k", ":fitness"))
                .isInstanceOf(IllegalStateException.class);
    }
}
