package com.cycling.delivery;

import com.cycling.messaging.MessageCodec;
import com.cycling.messaging.contract.DeliveryMessage;
import com.cycling.messaging.contract.DeliveryMessage.Target;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 12, 0);
    private static final DeliveryService.Retention RETENTION =
            new DeliveryService.Retention(Duration.ofDays(7), Duration.ofDays(90));
    private final Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);

    private final InMemoryDeliveries store = new InMemoryDeliveries();
    private final MessageCodec codec = new MessageCodec();
    private final ChannelSender mail = sender(DeliveryChannel.MAIL, true, "default");
    private final ChannelSender discord = sender(DeliveryChannel.DISCORD, true, "default", "reports");
    private final DeliveryMessage message = MessageFixtures.message("m1");

    private DeliveryService service(ChannelSender... senders) {
        return new DeliveryService(List.of(senders), store.repository, store.messageRepository, codec,
                TransactionOperations.withoutTransaction(), clock, RETENTION);
    }

    private static ChannelSender sender(DeliveryChannel channel, boolean enabled, String... destinations) {
        ChannelSender s = mock(ChannelSender.class);
        when(s.channel()).thenReturn(channel);
        when(s.isEnabled()).thenReturn(enabled);
        when(s.destinations()).thenReturn(Set.of(destinations));
        return s;
    }

    /** Legt Nachricht + Zeile so an, als waere sie bereits angenommen worden. */
    private Delivery queued(DeliveryChannel channel, String destination, LocalDateTime queuedAt) {
        store.messages.add(new StoredMessage("m1", "report", message.title(), queuedAt, codec.encode(message)));
        return store.add("m1", channel, destination, queuedAt);
    }

    // ---------- Annehmen ----------

    @Test
    void messageWithoutTargetsGoesToDefaultOfEveryEnabledChannel() {
        ChannelSender disabledDiscord = sender(DeliveryChannel.DISCORD, false, "default");

        List<Delivery> pending = service(mail, disabledDiscord).accept("report", message);

        assertThat(pending).singleElement().satisfies(d -> {
            assertThat(d.getChannel()).isEqualTo(DeliveryChannel.MAIL);
            assertThat(d.getDestination()).isEqualTo("default");
            assertThat(d.getSource()).isEqualTo("report");
            assertThat(d.getStatus()).isEqualTo(DeliveryStatus.PENDING);
            assertThat(d.getQueuedAt()).isEqualTo(NOW);
        });
        assertThat(store.message("m1")).hasValueSatisfying(m -> {
            assertThat(m.getSource()).isEqualTo("report");
            assertThat(m.getTitle()).isEqualTo("🚴 Morgenrunde");
            assertThat(codec.decode(m.getPayload(), DeliveryMessage.class)).isEqualTo(message);
        });
    }

    @Test
    void explicitTargetsQueueOnlyThoseDestinations() {
        DeliveryMessage targeted = MessageFixtures.withTargets(message, new Target("DISCORD", "reports"));

        List<Delivery> pending = service(mail, discord).accept("report", targeted);

        assertThat(pending).extracting(Delivery::getChannel, Delivery::getDestination)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(DeliveryChannel.DISCORD, "reports"));
    }

    @Test
    void channelNameInTargetIsCaseInsensitiveAndDuplicatesAreQueuedOnce() {
        DeliveryMessage targeted = MessageFixtures.withTargets(message,
                new Target("discord", "reports"), new Target("DISCORD", "reports"));

        assertThat(service(discord).accept("report", targeted)).hasSize(1);
        assertThat(store.rows).hasSize(1);
    }

    @Test
    void unknownDestinationFailsImmediatelyWithoutSending() throws Exception {
        DeliveryMessage targeted = MessageFixtures.withTargets(message, new Target("DISCORD", "alerts"));

        List<Delivery> pending = service(discord).accept("fitness", targeted);

        assertThat(pending).isEmpty();
        assertThat(store.rows).singleElement().satisfies(d -> {
            assertThat(d.getStatus()).isEqualTo(DeliveryStatus.FAILED);
            assertThat(d.getLastError()).contains("alerts").contains("DISCORD");
        });
        verify(discord, never()).send(any(), any(), any());
    }

    @Test
    void disabledOrUnknownChannelInTargetIsSkipped() {
        ChannelSender disabledMail = sender(DeliveryChannel.MAIL, false, "default");
        DeliveryMessage targeted = MessageFixtures.withTargets(message,
                new Target("MAIL", "default"), new Target("SMS", "default"), new Target("DISCORD", "default"));

        List<Delivery> pending = service(disabledMail, discord).accept("report", targeted);

        assertThat(pending).extracting(Delivery::getChannel).containsExactly(DeliveryChannel.DISCORD);
        assertThat(store.rows).hasSize(1);
    }

    @Test
    void sameMessageIdTwiceIsAcceptedOnlyOnce() {
        service(mail).accept("report", message);
        store.rows.getFirst().setStatus(DeliveryStatus.SENT);

        assertThat(service(mail).accept("report", message)).isEmpty();
        assertThat(store.rows).singleElement()
                .satisfies(d -> assertThat(d.getStatus()).isEqualTo(DeliveryStatus.SENT));
    }

    @Test
    void messageWithoutIdOrTitleIsRejected() {
        DeliveryMessage noTitle = new DeliveryMessage("m1", " ", null, null, null, null, null, null, null);
        DeliveryMessage noId = new DeliveryMessage(null, "Hallo", null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service(mail).accept("report", noTitle)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service(mail).accept("report", noId)).isInstanceOf(IllegalArgumentException.class);
        assertThat(store.rows).isEmpty();
        assertThat(store.messages).isEmpty();
    }

    @Test
    void enabledChannelsListsOnlyActiveSenders() {
        assertThat(service(mail, sender(DeliveryChannel.DISCORD, false)).enabledChannels())
                .containsExactly(DeliveryChannel.MAIL);
    }

    // ---------- Senden ----------

    @Test
    void deliverPendingSendsStoredMessageToDestinationAndMarksSent() throws Exception {
        Delivery row = queued(DeliveryChannel.DISCORD, "reports", NOW);

        service(discord).deliverPending("m1");

        verify(discord).send(eq(message), eq("reports"), eq(row));
        assertThat(row.getStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThat(row.getAttempts()).isEqualTo(1);
        assertThat(row.getLastAttemptAt()).isEqualTo(NOW);
    }

    @Test
    void temporaryFailureStaysPendingWithError() throws Exception {
        Delivery row = queued(DeliveryChannel.MAIL, "default", NOW);
        doThrow(DeliveryException.temporary("SMTP down", null)).when(mail).send(any(), any(), any());

        service(mail).deliverPending("m1");

        assertThat(row.getStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(row.getAttempts()).isEqualTo(1);
        assertThat(row.getLastError()).isEqualTo("SMTP down");
    }

    @Test
    void givesUpAfterFifthTemporaryFailure() throws Exception {
        Delivery row = queued(DeliveryChannel.MAIL, "default", NOW);
        row.setAttempts(4);
        doThrow(DeliveryException.temporary("SMTP down", null)).when(mail).send(any(), any(), any());

        service(mail).deliverPending("m1");

        assertThat(row.getAttempts()).isEqualTo(5);
        assertThat(row.getStatus()).isEqualTo(DeliveryStatus.FAILED);
    }

    @Test
    void permanentFailureFailsImmediately() throws Exception {
        Delivery row = queued(DeliveryChannel.DISCORD, "default", NOW);
        doThrow(DeliveryException.permanent("HTTP 401 (Bot-Token ungueltig)", null))
                .when(discord).send(any(), any(), any());

        service(discord).deliverPending("m1");

        assertThat(row.getAttempts()).isEqualTo(1);
        assertThat(row.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(row.getLastError()).contains("401");
    }

    @Test
    void unexpectedErrorInOneChannelCountsAsTemporaryAndOtherChannelStillGoesOut() throws Exception {
        Delivery mailRow = queued(DeliveryChannel.MAIL, "default", NOW);
        Delivery discordRow = store.add("m1", DeliveryChannel.DISCORD, "default", NOW);
        doThrow(new IllegalArgumentException("kaputt")).when(mail).send(any(), any(), any());

        service(mail, discord).deliverPending("m1");

        assertThat(mailRow.getStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(mailRow.getLastError()).contains("kaputt");
        assertThat(discordRow.getStatus()).isEqualTo(DeliveryStatus.SENT);
    }

    @Test
    void leavesRowsOfDisabledChannelUntouched() throws Exception {
        ChannelSender disabledDiscord = sender(DeliveryChannel.DISCORD, false, "default");
        Delivery row = queued(DeliveryChannel.DISCORD, "default", NOW);

        service(mail, disabledDiscord).deliverPending("m1");

        verify(disabledDiscord, never()).send(any(), any(), any());
        assertThat(row.getStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(row.getAttempts()).isZero();
    }

    @Test
    void pendingRowWhoseMessageIsGoneFailsPermanently() throws Exception {
        Delivery row = store.add("m1", DeliveryChannel.MAIL, "default", NOW);

        service(mail).deliverPending("m1");

        verify(mail, never()).send(any(), any(), any());
        assertThat(row.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(row.getLastError()).contains("nicht mehr gespeichert");
    }

    @Test
    void retryPendingResendsOnlyRowsQueuedWithinTwoDays() throws Exception {
        Delivery recent = queued(DeliveryChannel.MAIL, "default", NOW.minusHours(47));
        DeliveryMessage old = MessageFixtures.message("old");
        store.messages.add(new StoredMessage("old", "report", old.title(), NOW.minusDays(3), codec.encode(old)));
        Delivery tooOld = store.add("old", DeliveryChannel.MAIL, "default", NOW.minusDays(2).minusMinutes(1));

        service(mail).retryPending();

        assertThat(recent.getStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThat(tooOld.getStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(tooOld.getAttempts()).isZero();
    }

    // ---------- Aufraeumen ----------

    @Test
    void cleanUpDropsPayloadAfterSevenDaysAndRowsAfterNinetyDays() {
        StoredMessage fresh = new StoredMessage("fresh", "report", "t", NOW.minusDays(6), new byte[]{1});
        StoredMessage week = new StoredMessage("week", "report", "t", NOW.minusDays(8), new byte[]{1});
        StoredMessage ancient = new StoredMessage("ancient", "report", "t", NOW.minusDays(91), new byte[]{1});
        store.messages.addAll(List.of(fresh, week, ancient));
        store.add("week", DeliveryChannel.MAIL, "default", NOW.minusDays(8)).setStatus(DeliveryStatus.SENT);
        store.add("ancient", DeliveryChannel.MAIL, "default", NOW.minusDays(91)).setStatus(DeliveryStatus.FAILED);

        service(mail).cleanUp();

        assertThat(fresh.getPayload()).isNotNull();
        assertThat(week.getPayload()).isNull();
        assertThat(store.message("week")).isPresent();
        assertThat(store.rowsOf("week")).hasSize(1);
        assertThat(store.message("ancient")).isEmpty();
        assertThat(store.rowsOf("ancient")).isEmpty();
    }

    @Test
    void payloadRetentionShorterThanRetryWindowIsRejected() {
        assertThatThrownBy(() -> new DeliveryService.Retention(Duration.ofDays(1), Duration.ofDays(90)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("delivery.retention.payload");
        assertThatThrownBy(() -> new DeliveryService.Retention(Duration.ofDays(7), Duration.ofDays(3)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("delivery.retention.status");
    }
}
