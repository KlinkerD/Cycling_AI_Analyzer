package com.cycling.messaging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TopicsTest {

    @Test
    void exactFilterMatchesOnlySameTopic() {
        assertThat(Topics.matches("cycling/report/ready", "cycling/report/ready")).isTrue();
        assertThat(Topics.matches("cycling/report/ready", "cycling/report/resend")).isFalse();
    }

    @Test
    void plusMatchesExactlyOneLevel() {
        assertThat(Topics.matches(Topics.DELIVERY_REQUEST_ALL, "cycling/delivery/request/report")).isTrue();
        assertThat(Topics.matches(Topics.DELIVERY_REQUEST_ALL, "cycling/delivery/request/fitness")).isTrue();
        assertThat(Topics.matches(Topics.DELIVERY_REQUEST_ALL, "cycling/delivery/request")).isFalse();
        assertThat(Topics.matches(Topics.DELIVERY_REQUEST_ALL, "cycling/delivery/request/a/b")).isFalse();
    }

    @Test
    void hashMatchesAllRemainingLevels() {
        assertThat(Topics.matches("cycling/#", "cycling/delivery/request/report")).isTrue();
        assertThat(Topics.matches("cycling/#", "cycling")).isTrue();
        assertThat(Topics.matches("cycling/#", "other/x")).isFalse();
    }

    @Test
    void sourceIsLastLevelOfDeliveryRequestTopic() {
        assertThat(Topics.DELIVERY_REQUEST_REPORT).isEqualTo("cycling/delivery/request/report");
        assertThat(Topics.deliveryRequest("fitness")).isEqualTo("cycling/delivery/request/fitness");
        assertThat(Topics.deliverySource("cycling/delivery/request/fitness")).isEqualTo("fitness");
    }

    @Test
    void aiRequestAndResultCarryTheSource() {
        assertThat(Topics.aiRequest("report")).isEqualTo("cycling/ai/request/report");
        assertThat(Topics.aiResult("training")).isEqualTo("cycling/ai/result/training");
        assertThat(Topics.matches(Topics.AI_REQUEST_ALL, Topics.aiRequest("training"))).isTrue();
        assertThat(Topics.matches(Topics.AI_REQUEST_ALL, Topics.aiResult("training"))).isFalse();
        assertThat(Topics.source(Topics.aiRequest("report"))).isEqualTo("report");
    }

    @Test
    void intervalsTopicsAreBelowIntervalsLevel() {
        assertThat(Topics.INTERVALS_ACTIVITY_DETECTED).isEqualTo("cycling/intervals/activity/detected");
        assertThat(Topics.INTERVALS_WELLNESS_UPDATED).isEqualTo("cycling/intervals/wellness/updated");
        assertThat(Topics.matches("cycling/intervals/#", Topics.INTERVALS_WELLNESS_UPDATED)).isTrue();
    }
}
