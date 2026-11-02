package com.cycling.delivery;

import com.cycling.dto.ReportDto;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MailReportSenderTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final MailReportSender sender = new MailReportSender(
            new MailSettings(true, "", "me@example.com", "Cycling Report", " <you@example.com> ", "[Cycling Report]"),
            mailSender);

    private final ReportDto report = ReportFixtures.report("i1");
    private final ReportCharts charts = new ReportCharts(List.of(
            new ReportCharts.Chart(ReportCharts.SPEED_ALT, "Geschwindigkeit", ReportFixtures.png("speed"))));

    MailReportSenderTest() {
        when(mailSender.createMimeMessage())
                .thenAnswer(inv -> new MimeMessage(jakarta.mail.Session.getInstance(new Properties())));
    }

    @Test
    void sendsHtmlMailWithChartsInlineToConfiguredRecipient() throws Exception {
        sender.send(report, charts, new ReportDelivery("i1", DeliveryChannel.MAIL));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage mail = captor.getValue();
        mail.saveChanges();

        assertThat(((InternetAddress) mail.getAllRecipients()[0]).getAddress()).isEqualTo("you@example.com");
        assertThat(((InternetAddress) mail.getFrom()[0]).getAddress()).isEqualTo("me@example.com");
        assertThat(mail.getSubject()).isEqualTo("[Cycling Report] Morgenrunde (01.11.2026)");

        List<Part> parts = new ArrayList<>();
        collectParts(mail, parts);
        assertThat(parts).anySatisfy(p -> assertThat(p.getContent().toString())
                .contains("Morgenrunde").contains("cid:speedAltChart"));
        assertThat(parts).anySatisfy(p -> assertThat(((jakarta.mail.internet.MimePart) p).getContentID())
                .isEqualTo("<speedAltChart>"));
    }

    @Test
    void smtpLoginFailureIsPermanent() {
        doThrow(new MailAuthenticationException("bad login")).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> sender.send(report, charts, new ReportDelivery("i1", DeliveryChannel.MAIL)))
                .isInstanceOfSatisfying(DeliveryException.class, e -> assertThat(e.isPermanent()).isTrue());
    }

    @Test
    void smtpConnectionFailureIsTemporary() {
        doThrow(new MailSendException("timeout")).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> sender.send(report, charts, new ReportDelivery("i1", DeliveryChannel.MAIL)))
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
