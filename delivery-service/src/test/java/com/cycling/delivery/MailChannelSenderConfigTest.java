package com.cycling.delivery;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MailChannelSenderConfigTest {

    private static MailChannelSender sender(MailSettings settings) {
        return new MailChannelSender(settings, new JavaMailSenderImpl());
    }

    private static MailSettings enabled(String from, String fallbackUsername, Map<String, String> destinations) {
        return new MailSettings(true, from, fallbackUsername, "Cycling Report", destinations, "[Cycling Report]");
    }

    @Test
    void failsOnStartupWhenEnabledWithoutRecipient() {
        assertThatThrownBy(() -> sender(enabled("me@example.com", "", Map.of("default", ""))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("delivery.mail.destinations");
    }

    @Test
    void failsOnStartupWhenRecipientIsNoValidAddress() {
        assertThatThrownBy(() -> sender(enabled("me@example.com", "", Map.of("reports", "xx"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("delivery.mail.destinations.reports");
    }

    @Test
    void failsOnStartupWhenNeitherSenderNorSmtpUsernameIsValid() {
        assertThatThrownBy(() -> sender(enabled("", "xx", Map.of("default", "you@example.com"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("delivery.mail.from");
    }

    @Test
    void usesSmtpUsernameAsSenderWhenFromIsEmpty() {
        assertThatCode(() -> sender(enabled("", "me@example.com", Map.of("default", "you@example.com"))))
                .doesNotThrowAnyException();
    }

    @Test
    void knowsAllConfiguredDestinations() {
        MailChannelSender sender = sender(enabled("me@example.com", "",
                Map.of("default", "you@example.com", "alerts", "<ops@example.com>")));

        assertThat(sender.destinations()).containsExactlyInAnyOrder("default", "alerts");
    }

    @Test
    void startsWhenDisabledEvenWithoutValues() {
        assertThatCode(() -> sender(new MailSettings(false, "", "", "", Map.of(), "")))
                .doesNotThrowAnyException();
    }
}
