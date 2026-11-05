package com.cycling.delivery;

import com.cycling.messaging.contract.DeliveryMessage;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimePart;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MailChannelSenderTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final MailChannelSender sender = new MailChannelSender(
            new MailSettings(true, "", "me@example.com", "Cycling Report",
                    Map.of("default", " <you@example.com> ", "alerts", "ops@example.com"), "[Cycling Report]"),
            mailSender);

    private final DeliveryMessage message = MessageFixtures.withAttachments(MessageFixtures.message("m1"),
            MessageFixtures.attachment("speedAltChart", "Geschwindigkeit"));

    MailChannelSenderTest() {
        when(mailSender.createMimeMessage())
                .thenAnswer(inv -> new MimeMessage(jakarta.mail.Session.getInstance(new Properties())));
    }

    private static Delivery delivery(String destination) {
        return new Delivery(MessageFixtures.id("m1"), "report", DeliveryChannel.MAIL, destination, LocalDateTime.now());
    }

    private MimeMessage sent() throws Exception {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage mail = captor.getValue();
        mail.saveChanges();
        return mail;
    }

    @Test
    void sendsHtmlMailWithAttachmentsInlineToDestinationAddress() throws Exception {
        sender.send(message, "default", delivery("default"));

        MimeMessage mail = sent();
        assertThat(((InternetAddress) mail.getAllRecipients()[0]).getAddress()).isEqualTo("you@example.com");
        assertThat(((InternetAddress) mail.getFrom()[0]).getAddress()).isEqualTo("me@example.com");
        assertThat(mail.getSubject()).isEqualTo("[Cycling Report] 🚴 Morgenrunde (01.11.2026)");

        List<Part> parts = new ArrayList<>();
        collectParts(mail, parts);
        assertThat(parts).anySatisfy(p -> assertThat(p.getContent().toString())
                .contains("Morgenrunde")
                .contains("8 / 10")
                .contains("Gleichmaessig getreten")
                .contains("Analysiert am 01.11.2026 10:00 Uhr")
                .contains("cid:speedAltChart"));
        assertThat(parts).anySatisfy(p -> assertThat(((MimePart) p).getContentID()).isEqualTo("<speedAltChart>"));
    }

    @Test
    void otherDestinationGoesToItsOwnAddress() throws Exception {
        sender.send(message, "alerts", delivery("alerts"));

        assertThat(((InternetAddress) sent().getAllRecipients()[0]).getAddress()).isEqualTo("ops@example.com");
    }

    @Test
    void htmlInTextsIsEscaped() throws Exception {
        DeliveryMessage evil = MessageFixtures.withSections(message,
                MessageFixtures.sections("<script>x</script>", "b", "c", "d"));

        sender.send(evil, "default", delivery("default"));

        List<Part> parts = new ArrayList<>();
        collectParts(sent(), parts);
        assertThat(parts).anySatisfy(p -> assertThat(p.getContent().toString())
                .contains("&lt;script&gt;").doesNotContain("<script>"));
    }

    @Test
    void unknownDestinationIsPermanent() {
        assertThatThrownBy(() -> sender.send(message, "reports", delivery("reports")))
                .isInstanceOfSatisfying(DeliveryException.class, e -> assertThat(e.isPermanent()).isTrue());
    }

    @Test
    void smtpLoginFailureIsPermanent() {
        doThrow(new MailAuthenticationException("bad login")).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> sender.send(message, "default", delivery("default")))
                .isInstanceOfSatisfying(DeliveryException.class, e -> assertThat(e.isPermanent()).isTrue());
    }

    @Test
    void smtpConnectionFailureIsTemporary() {
        doThrow(new MailSendException("timeout")).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> sender.send(message, "default", delivery("default")))
                .isInstanceOfSatisfying(DeliveryException.class, e -> assertThat(e.isPermanent()).isFalse());
    }

    private static void collectParts(Part part, List<Part> out) throws Exception {
        Object content = part.getContent();
        if (content instanceof Multipart mp) {
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart child = mp.getBodyPart(i);
                collectParts(child, out);
            }
        } else {
            out.add(part);
        }
    }
}
