package com.cycling.delivery;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscordChannelSenderConfigTest {

    private static DiscordChannelSender sender(DiscordSettings settings) {
        return new DiscordChannelSender(settings, RestClient.create(), duration -> { });
    }

    private static DiscordSettings settings(boolean enabled, String token, Map<String, String> destinations) {
        return new DiscordSettings(enabled, token, destinations, DiscordSettings.DEFAULT_API_BASE_URL);
    }

    @Test
    void failsOnStartupWhenEnabledWithoutBotToken() {
        assertThatThrownBy(() -> sender(settings(true, " ", Map.of("default", "123"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("delivery.discord.bot-token");
    }

    @Test
    void failsOnStartupWhenEnabledWithoutAnyDestination() {
        assertThatThrownBy(() -> sender(settings(true, "token", Map.of("default", " "))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("delivery.discord.destinations");
    }

    @Test
    void blankDestinationsAreIgnoredAndOthersAreKnown() {
        DiscordChannelSender sender = sender(settings(true, "token", Map.of("default", "", "reports", " 456 ")));

        assertThat(sender.destinations()).containsExactly("reports");
    }

    @Test
    void startsWhenDisabledEvenWithoutValues() {
        assertThatCode(() -> sender(settings(false, "", Map.of()))).doesNotThrowAnyException();
    }
}
