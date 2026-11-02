package com.cycling.delivery;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MailReportSenderConfigTest {

    private static MailReportSender sender(MailSettings settings) {
        return new MailReportSender(settings, new JavaMailSenderImpl());
    }

    private static MailSettings enabled(String from, String fallbackUsername, String to) {
        return new MailSettings(true, from, fallbackUsername, "Cycling Report", to, "[Cycling Report]");
    }

    @Test
    void failsOnStartupWhenEnabledWithoutRecipient() {
        assertThatThrownBy(() -> sender(enabled("me@example.com", "", "")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cycling.report.mail.to");
    }

    @Test
    void failsOnStartupWhenRecipientIsNoValidAddress() {
        assertThatThrownBy(() -> sender(enabled("me@example.com", "", "xx")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cycling.report.mail.to");
    }

    @Test
    void failsOnStartupWhenNeitherSenderNorSmtpUsernameIsValid() {
        assertThatThrownBy(() -> sender(enabled("", "xx", "you@example.com")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cycling.report.mail.from");
    }

    @Test
    void usesSmtpUsernameAsSenderWhenFromIsEmpty() {
        assertThatCode(() -> sender(enabled("", "me@example.com", "you@example.com")))
                .doesNotThrowAnyException();
    }

    @Test
    void startsWhenDisabledEvenWithoutValues() {
        assertThatCode(() -> sender(new MailSettings(false, "", "", "", "", "")))
                .doesNotThrowAnyException();
    }
}
