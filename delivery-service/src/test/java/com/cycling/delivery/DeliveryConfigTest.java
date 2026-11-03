package com.cycling.delivery;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Prueft die Verdrahtung ueber die Config-Keys und den Startabbruch bei unvollstaendiger Config. */
class DeliveryConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(DeliveryConfig.class)
            .withBean(DeliveryRepository.class, () -> mock(DeliveryRepository.class))
            .withBean(MessageRepository.class, () -> mock(MessageRepository.class))
            .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
            .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class));

    @Test
    void bothChannelsAreOffByDefault() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(DeliveryService.class).enabledChannels()).isEmpty();
        });
    }

    @Test
    void channelsAreSwitchedOnIndependentlyByConfigKeys() {
        runner.withPropertyValues(
                        "delivery.discord.enabled=true",
                        "delivery.discord.bot-token=abc",
                        "delivery.discord.destinations.default=123")
                .run(context -> assertThat(context.getBean(DeliveryService.class).enabledChannels())
                        .containsExactly(DeliveryChannel.DISCORD));
    }

    @Test
    void additionalDestinationsAreBoundFromMapKeys() {
        runner.withPropertyValues(
                        "delivery.discord.enabled=true",
                        "delivery.discord.bot-token=abc",
                        "delivery.discord.destinations.default=123",
                        "delivery.discord.destinations.reports=456",
                        "delivery.discord.destinations.alerts=789")
                .run(context -> assertThat(context.getBean(DiscordChannelSender.class).destinations())
                        .containsExactlyInAnyOrder("default", "reports", "alerts"));
    }

    @Test
    void startupFailsWhenDiscordIsEnabledWithoutToken() {
        runner.withPropertyValues(
                        "delivery.discord.enabled=true",
                        "delivery.discord.destinations.default=123")
                .run(context -> assertThat(context).getFailure()
                        .rootCause().hasMessageContaining("delivery.discord.bot-token"));
    }

    @Test
    void startupFailsWhenPayloadRetentionIsShorterThanRetryWindow() {
        runner.withPropertyValues("delivery.retention.payload=1d")
                .run(context -> assertThat(context).getFailure()
                        .rootCause().hasMessageContaining("delivery.retention.payload"));
    }
}
