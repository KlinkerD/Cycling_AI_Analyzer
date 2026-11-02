package com.cycling.delivery;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscordReportSenderConfigTest {

    private static DiscordReportSender sender(DiscordSettings settings) {
        return new DiscordReportSender(settings, RestClient.create(), duration -> { });
    }

    @Test
    void failsOnStartupWhenEnabledWithoutBotToken() {
        assertThatThrownBy(() -> sender(new DiscordSettings(true, " ", "123", DiscordSettings.DEFAULT_API_BASE_URL)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cycling.report.discord.bot-token");
    }

    @Test
    void failsOnStartupWhenEnabledWithoutChannelId() {
        assertThatThrownBy(() -> sender(new DiscordSettings(true, "token", "", DiscordSettings.DEFAULT_API_BASE_URL)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cycling.report.discord.channel-id");
    }

    @Test
    void startsWhenDisabledEvenWithoutValues() {
        assertThatCode(() -> sender(new DiscordSettings(false, "", "", DiscordSettings.DEFAULT_API_BASE_URL)))
                .doesNotThrowAnyException();
    }
}
