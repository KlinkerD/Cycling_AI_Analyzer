package com.cycling.delivery;

import com.cycling.messaging.MessageCodec;
import com.cycling.messaging.contract.DeliveryMessage;
import com.cycling.messaging.contract.DeliveryMessage.Target;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import java.util.Set;

/**
 * Nimmt {@link DeliveryMessage}s an, speichert sie und versendet sie an alle Ziele, inklusive
 * gespeicherter Wiederholung. Pro (messageId, Kanal, Ziel) gibt es eine Zeile in {@code delivery}.
 */
public class DeliveryService {

    /** Erster Versand + 4 Wiederholungen (bei 15-Minuten-Takt also rund 1 Stunde). */
    static final int MAX_ATTEMPTS = 5;
    /** Aeltere offene Zeilen werden nicht mehr automatisch nachgesendet. */
    static final Duration RETRY_WINDOW = Duration.ofDays(2);
    static final String DEFAULT_DESTINATION = "default";
    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);

    /**
     * Aufbewahrung: Inhalt (Bilder) nach {@code payload}, Statuszeilen nach {@code status}.
     * Der Inhalt muss mindestens so lange bleiben, wie noch wiederholt wird.
     */
    public record Retention(Duration payload, Duration status) {
        public Retention {
            if (payload.compareTo(RETRY_WINDOW) < 0) {
                throw new IllegalStateException("delivery.retention.payload (" + payload
                        + ") muss mindestens so lang sein wie das Retry-Zeitfenster (" + RETRY_WINDOW + ").");
            }
            if (status.compareTo(payload) < 0) {
                throw new IllegalStateException("delivery.retention.status (" + status
                        + ") muss mindestens so lang sein wie delivery.retention.payload (" + payload + ").");
            }
        }
    }

    private final List<ChannelSender> senders;
    private final DeliveryRepository deliveries;
    private final MessageRepository messages;
    private final MessageCodec codec;
    private final TransactionOperations transactions;
    private final Clock clock;
    private final Retention retention;

    public DeliveryService(List<ChannelSender> senders, DeliveryRepository deliveries, MessageRepository messages,
                           MessageCodec codec, TransactionOperations transactions, Clock clock, Retention retention) {
        this.senders = senders;
        this.deliveries = deliveries;
        this.messages = messages;
        this.codec = codec;
        this.transactions = transactions;
        this.clock = clock;
        this.retention = retention;
    }

    /** Kanaele, die per Config eingeschaltet sind. */
    public List<DeliveryChannel> enabledChannels() {
        return senders.stream().filter(ChannelSender::isEnabled).map(ChannelSender::channel).toList();
    }

    /**
     * Speichert die Nachricht und legt pro Ziel eine Zeile an. Ohne Ziele geht sie an "default" jedes
     * aktiven Kanals. Eine schon bekannte messageId (MQTT-Neuzustellung) wird ignoriert. Unbekannte
     * Ziele werden sofort FAILED, unbekannte oder abgeschaltete Kanaele uebersprungen.
     *
     * @return die neu eingereihten, zu sendenden Zeilen
     */
    public List<Delivery> accept(String source, DeliveryMessage message) {
        if (message.messageId() == null) {
            throw new IllegalArgumentException("Nachricht ohne messageId von Quelle " + source);
        }
        if (message.title() == null || message.title().isBlank()) {
            throw new IllegalArgumentException("Nachricht " + message.messageId() + " von " + source + " ohne Titel");
        }
        List<Delivery> pending = transactions.execute(status -> store(source, message));
        return pending == null ? List.of() : pending;
    }

    private List<Delivery> store(String source, DeliveryMessage message) {
        if (messages.existsById(message.messageId())) {
            log.info("Nachricht {} von {} bereits bekannt - kein zweiter Versand", message.messageId(), source);
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now(clock);
        messages.save(new StoredMessage(message.messageId(), source, message.title(), now, codec.encode(message)));

        List<Delivery> pending = new ArrayList<>();
        for (Route route : routes(message)) {
            ChannelSender sender = route.sender();
            Delivery delivery = new Delivery(message.messageId(), source, sender.channel(), route.destination(), now);
            if (!sender.destinations().contains(route.destination())) {
                delivery.setStatus(DeliveryStatus.FAILED);
                delivery.setLastError("Unbekanntes Ziel '" + route.destination() + "' fuer Kanal " + sender.channel()
                        + " (konfiguriert: " + sender.destinations() + ")");
                log.error("Nachricht {} von {}: {}", message.messageId(), source, delivery.getLastError());
            } else {
                pending.add(delivery);
            }
            deliveries.save(delivery);
        }
        log.info("Nachricht {} von {} angenommen: {}", message.messageId(), source,
                pending.stream().map(d -> d.getChannel() + ":" + d.getDestination()).toList());
        return pending;
    }

    private record Route(ChannelSender sender, String destination) {
    }

    private List<Route> routes(DeliveryMessage message) {
        Set<Route> routes = new LinkedHashSet<>();
        if (message.targets().isEmpty()) {
            senders.stream().filter(ChannelSender::isEnabled)
                    .forEach(s -> routes.add(new Route(s, DEFAULT_DESTINATION)));
            return List.copyOf(routes);
        }
        for (Target target : message.targets()) {
            Optional<DeliveryChannel> channel = DeliveryChannel.parse(target.channel());
            if (channel.isEmpty()) {
                log.warn("Nachricht {}: unbekannter Kanal '{}' uebersprungen", message.messageId(), target.channel());
                continue;
            }
            ChannelSender sender = enabledSender(channel.get());
            if (sender == null) {
                log.warn("Nachricht {}: Kanal {} ist nicht aktiv, Ziel '{}' uebersprungen",
                        message.messageId(), channel.get(), target.destination());
                continue;
            }
            String destination = target.destination() == null || target.destination().isBlank()
                    ? DEFAULT_DESTINATION : target.destination().trim();
            routes.add(new Route(sender, destination));
        }
        return List.copyOf(routes);
    }

    /** Sendet alle offenen Zeilen dieser Nachricht (nur aktive Kanaele). */
    public void deliverPending(UUID messageId) {
        List<Delivery> pending = pendingForEnabledChannels(messageId);
        if (pending.isEmpty()) return;

        DeliveryMessage message = messages.findById(messageId)
                .map(StoredMessage::getPayload)
                .map(payload -> codec.decode(payload, DeliveryMessage.class))
                .orElse(null);
        if (message == null) {
            for (Delivery delivery : pending) {
                startAttempt(delivery);
                recordFailure(delivery, "Nachricht nicht mehr gespeichert (aufgeraeumt)", true);
                deliveries.save(delivery);
            }
            return;
        }
        for (Delivery delivery : pending) {
            attempt(enabledSender(delivery.getChannel()), message, delivery);
        }
    }

    /** Wird vom Scheduler aufgerufen: offene Zeilen der letzten zwei Tage erneut versuchen. */
    public void retryPending() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minus(RETRY_WINDOW);
        deliveries.findByStatusAndQueuedAtAfter(DeliveryStatus.PENDING, cutoff).stream()
                .map(Delivery::getMessageId)
                .distinct()
                .toList()
                .forEach(this::deliverPending);
    }

    /** Loescht alte Inhalte (Bilder) und sehr alte Statuszeilen. */
    public void cleanUp() {
        LocalDateTime now = LocalDateTime.now(clock);
        int payloads = messages.clearPayloadsReceivedBefore(now.minus(retention.payload()));
        long rows = deliveries.deleteByQueuedAtBefore(now.minus(retention.status()));
        long removed = messages.deleteByReceivedAtBefore(now.minus(retention.status()));
        if (payloads + rows + removed > 0) {
            log.info("Aufgeraeumt: {} Nachrichteninhalte, {} Statuszeilen, {} Nachrichten", payloads, rows, removed);
        }
    }

    private void attempt(ChannelSender sender, DeliveryMessage message, Delivery delivery) {
        startAttempt(delivery);
        try {
            sender.send(message, delivery.getDestination(), delivery);
            delivery.setStatus(DeliveryStatus.SENT);
            delivery.setLastError(null);
            log.info("Nachricht {} per {}:{} versendet.", delivery.getMessageId(), delivery.getChannel(),
                    delivery.getDestination());
        } catch (DeliveryException e) {
            recordFailure(delivery, e.getMessage(), e.isPermanent());
        } catch (RuntimeException e) {
            // Unerwarteter Fehler: wie voruebergehend behandeln, andere Kanaele laufen weiter.
            recordFailure(delivery, e.getClass().getSimpleName() + ": " + e.getMessage(), false);
        }
        deliveries.save(delivery);
    }

    private void startAttempt(Delivery delivery) {
        delivery.setAttempts(delivery.getAttempts() + 1);
        delivery.setLastAttemptAt(LocalDateTime.now(clock));
    }

    /** Offene Zeilen; die abgeschalteter Kanaele bleiben PENDING, bis der Kanal wieder aktiv ist. */
    private List<Delivery> pendingForEnabledChannels(UUID messageId) {
        return deliveries.findByMessageIdAndStatus(messageId, DeliveryStatus.PENDING).stream()
                .filter(d -> enabledSender(d.getChannel()) != null)
                .toList();
    }

    private void recordFailure(Delivery delivery, String error, boolean permanent) {
        delivery.setLastError(error);
        if (permanent || delivery.getAttempts() >= MAX_ATTEMPTS) {
            delivery.setStatus(DeliveryStatus.FAILED);
            log.error("Nachricht {} per {}:{} endgueltig fehlgeschlagen nach {} Versuch(en): {}",
                    delivery.getMessageId(), delivery.getChannel(), delivery.getDestination(),
                    delivery.getAttempts(), error);
        } else {
            log.warn("Nachricht {} per {}:{} fehlgeschlagen (Versuch {}/{}), neuer Versuch spaeter: {}",
                    delivery.getMessageId(), delivery.getChannel(), delivery.getDestination(),
                    delivery.getAttempts(), MAX_ATTEMPTS, error);
        }
    }

    private ChannelSender enabledSender(DeliveryChannel channel) {
        return senders.stream().filter(s -> s.channel() == channel && s.isEnabled()).findFirst().orElse(null);
    }
}
