package com.cycling.report;

import com.cycling.messaging.contract.DeliveryMessage.Target;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportTargetsTest {

    @Test
    void emptyConfigMeansNoTargets() {
        assertThat(ReportTargets.parse("")).isEmpty();
        assertThat(ReportTargets.parse(null)).isEmpty();
        assertThat(ReportTargets.parse("  ")).isEmpty();
    }

    @Test
    void parsesChannelAndDestinationPairs() {
        assertThat(ReportTargets.parse(" discord:reports , MAIL:default "))
                .containsExactly(new Target("DISCORD", "reports"), new Target("MAIL", "default"));
    }

    @Test
    void rejectsEntriesWithoutDestination() {
        assertThatThrownBy(() -> ReportTargets.parse("DISCORD"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("report.targets");
        assertThatThrownBy(() -> ReportTargets.parse("DISCORD:"))
                .isInstanceOf(IllegalStateException.class);
    }
}
