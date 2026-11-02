package com.cycling.delivery;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Prueft die Verdrahtung ueber die Config-Keys und den Startabbruch bei unvollstaendiger Config. */
class DeliveryConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(DeliveryConfig.class)
            .withBean(ReportDeliveryRepository.class, () -> mock(ReportDeliveryRepository.class))
            .withBean(ReportChartRenderer.class, () -> mock(ReportChartRenderer.class))
            .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
            .withPropertyValues("analysis-service.base-url=http://localhost:8080");

    @Test
    void bothChannelsAreOffByDefault() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ReportDeliveryService.class).enabledChannels()).isEmpty();
        });
    }

    @Test
    void channelsAreSwitchedOnIndependentlyByConfigKeys() {
        runner.withPropertyValues(
                        "cycling.report.discord.enabled=true",
                        "cycling.report.discord.bot-token=abc",
                        "cycling.report.discord.channel-id=123")
                .run(context -> assertThat(context.getBean(ReportDeliveryService.class).enabledChannels())
                        .containsExactly(DeliveryChannel.DISCORD));
    }

    @Test
    void startupFailsWhenDiscordIsEnabledWithoutToken() {
        runner.withPropertyValues(
                        "cycling.report.discord.enabled=true",
                        "cycling.report.discord.channel-id=123")
                .run(context -> assertThat(context).getFailure()
                        .rootCause().hasMessageContaining("cycling.report.discord.bot-token"));
    }
}
