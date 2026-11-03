package com.cycling.messaging;

import org.eclipse.paho.mqttv5.client.DisconnectedBufferOptions;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttActionListener;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Duenne Huelle um den Paho-MQTT-v5-Client fuer alle Services.
 *
 * <ul>
 *   <li>Persistente Session (clean start = false): Nachrichten, die waehrend eines Neustarts
 *       eintreffen, liefert der Broker nach dem Reconnect nach.</li>
 *   <li>Eingehende Nachrichten laufen nacheinander auf einem eigenen Worker-Thread; das PUBACK geht
 *       erst raus, wenn der Handler fertig ist. Bricht die Verbindung vorher ab, kommt die Nachricht
 *       erneut - Handler muessen daher idempotent sein.</li>
 *   <li>Der erste Verbindungsaufbau wird wiederholt, bis der Broker erreichbar ist; danach uebernimmt
 *       Pahos automatischer Reconnect. Waehrend einer Unterbrechung werden Publishes gepuffert.</li>
 * </ul>
 *
 * Handler mit {@link #subscribe} vor {@link #start()} registrieren.
 */
public class MqttMessaging implements MessagePublisher, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(MqttMessaging.class);
    private static final int QOS = 1;
    private static final long INITIAL_RETRY_SECONDS = 5;

    private final MqttSettings settings;
    private final MessageCodec codec = new MessageCodec();
    /** Topic-Filter -> Handler (bekommt konkretes Topic + Payload). */
    private final Map<String, BiConsumer<String, byte[]>> handlers = new ConcurrentHashMap<>();
    private final MqttAsyncClient client;
    private final ExecutorService worker =
            Executors.newSingleThreadExecutor(Thread.ofPlatform().name("mqtt-worker").factory());
    private final ScheduledExecutorService connector =
            Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("mqtt-connect").daemon().factory());
    private volatile boolean closed;
    private volatile Runnable onConnect = () -> { };
    private volatile WillMessage will;

    private record WillMessage(String topic, byte[] payload) {
    }

    public MqttMessaging(MqttSettings settings) {
        this.settings = settings;
        try {
            this.client = new MqttAsyncClient(settings.brokerUrl(), settings.clientId(), new MemoryPersistence());
        } catch (MqttException e) {
            throw new IllegalStateException("MQTT-Client konnte nicht erzeugt werden: " + e.getMessage(), e);
        }
        client.setCallback(new Callback());
        client.setManualAcks(true);

        DisconnectedBufferOptions buffer = new DisconnectedBufferOptions();
        buffer.setBufferEnabled(true);
        buffer.setBufferSize(1000);
        buffer.setPersistBuffer(false);
        buffer.setDeleteOldestMessages(false);
        client.setBufferOpts(buffer);
    }

    /** Registriert einen Handler fuer dieses Topic bzw. diesen Filter. */
    public <T> void subscribe(String topicFilter, Class<T> type, Consumer<T> handler) {
        subscribe(topicFilter, type, (topic, message) -> handler.accept(message));
    }

    /**
     * Registriert einen Handler fuer einen Topic-Filter (Wildcards "+" und "#" erlaubt). Der Handler
     * bekommt das konkrete Topic mit, z. B. um die Quelle aus dem letzten Teil abzulesen.
     */
    public <T> void subscribe(String topicFilter, Class<T> type, BiConsumer<String, T> handler) {
        handlers.put(topicFilter, (topic, payload) -> handler.accept(topic, codec.decode(payload, type)));
    }

    /**
     * Letzter Wille: der Broker veroeffentlicht diese Nachricht (retained), wenn die Verbindung
     * ohne sauberes Disconnect abreisst. Vor {@link #start()} setzen.
     */
    public void setWill(String topic, Object message) {
        this.will = new WillMessage(topic, codec.encode(message));
    }

    /** Wird nach jedem (Re-)Connect ausgefuehrt, z. B. um einen Status retained zu veroeffentlichen. */
    public void onConnect(Runnable action) {
        this.onConnect = action;
    }

    /** Wie {@link #publish}, aber retained: neue Subscriber bekommen die letzte Nachricht sofort. */
    public void publishRetained(String topic, Object message) {
        MqttMessage mqttMessage = new MqttMessage(codec.encode(message));
        mqttMessage.setQos(QOS);
        mqttMessage.setRetained(true);
        try {
            client.publish(topic, mqttMessage);
        } catch (MqttException e) {
            throw new MessagingException("Publish auf " + topic + " fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    /** Startet den (asynchronen) Verbindungsaufbau. */
    public void start() {
        connector.execute(this::connect);
    }

    public boolean isConnected() {
        return client.isConnected();
    }

    @Override
    public void publish(String topic, Object message) {
        MqttMessage mqttMessage = new MqttMessage(codec.encode(message));
        mqttMessage.setQos(QOS);
        try {
            client.publish(topic, mqttMessage);
        } catch (MqttException e) {
            throw new MessagingException("Publish auf " + topic + " fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        closed = true;
        connector.shutdownNow();
        try {
            if (client.isConnected()) {
                // Bei sauberem Disconnect schickt der Broker den Last Will nicht - also selbst melden.
                WillMessage currentWill = will;
                if (currentWill != null) {
                    MqttMessage offline = new MqttMessage(currentWill.payload());
                    offline.setQos(QOS);
                    offline.setRetained(true);
                    client.publish(currentWill.topic(), offline).waitForCompletion(2_000);
                }
                client.disconnect().waitForCompletion(5_000);
            }
            client.close();
        } catch (MqttException e) {
            log.debug("MQTT-Verbindung nicht sauber geschlossen: {}", e.getMessage());
        }
        worker.shutdown();
    }

    private void connect() {
        if (closed) return;
        try {
            client.connect(connectionOptions(), null, new MqttActionListener() {
                @Override
                public void onSuccess(IMqttToken token) {
                    // Subscribes erledigt connectComplete, das auch nach jedem Reconnect laeuft.
                }

                @Override
                public void onFailure(IMqttToken token, Throwable e) {
                    retryInitialConnect(e);
                }
            });
        } catch (MqttException e) {
            retryInitialConnect(e);
        }
    }

    private void retryInitialConnect(Throwable e) {
        if (closed) return;
        log.warn("MQTT-Broker {} nicht erreichbar ({}), neuer Versuch in {} s",
                settings.brokerUrl(), e.getMessage(), INITIAL_RETRY_SECONDS);
        connector.schedule(this::connect, INITIAL_RETRY_SECONDS, TimeUnit.SECONDS);
    }

    private MqttConnectionOptions connectionOptions() {
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(false);
        options.setSessionExpiryInterval(settings.sessionExpiry().toSeconds());
        options.setAutomaticReconnect(true);
        options.setAutomaticReconnectDelay(1, 60);
        options.setKeepAliveInterval(30);
        options.setConnectionTimeout(10);
        // Broker schickt hoechstens so viele unbestaetigte Nachrichten gleichzeitig.
        options.setReceiveMaximum(10);
        WillMessage currentWill = will;
        if (currentWill != null) {
            MqttMessage willMessage = new MqttMessage(currentWill.payload());
            willMessage.setQos(QOS);
            willMessage.setRetained(true);
            options.setWill(currentWill.topic(), willMessage);
        }
        if (settings.username() != null && !settings.username().isBlank()) {
            options.setUserName(settings.username());
            if (settings.password() != null) {
                options.setPassword(settings.password().getBytes(StandardCharsets.UTF_8));
            }
        }
        return options;
    }

    private void subscribeAll() {
        if (handlers.isEmpty()) return;
        MqttSubscription[] subscriptions = handlers.keySet().stream()
                .map(topic -> new MqttSubscription(topic, QOS))
                .toArray(MqttSubscription[]::new);
        try {
            client.subscribe(subscriptions);
        } catch (MqttException e) {
            log.error("MQTT-Subscribe fehlgeschlagen: {}", e.getMessage(), e);
        }
    }

    private void handle(String topic, MqttMessage message) {
        BiConsumer<String, byte[]> handler = handlerFor(topic);
        try {
            if (handler == null) {
                log.warn("Kein Handler fuer Topic {}, Nachricht verworfen", topic);
            } else {
                handler.accept(topic, message.getPayload());
            }
        } catch (RuntimeException e) {
            // Nicht erneut zustellen lassen: eine kaputte Nachricht soll die Queue nicht blockieren.
            log.error("Nachricht auf {} konnte nicht verarbeitet werden: {}", topic, e.getMessage(), e);
        } finally {
            acknowledge(message);
        }
    }

    /** Exakter Treffer zuerst, sonst der erste passende Wildcard-Filter. */
    private BiConsumer<String, byte[]> handlerFor(String topic) {
        BiConsumer<String, byte[]> exact = handlers.get(topic);
        if (exact != null) return exact;
        return handlers.entrySet().stream()
                .filter(e -> Topics.matches(e.getKey(), topic))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private void acknowledge(MqttMessage message) {
        if (message.getQos() == 0) return;
        try {
            client.messageArrivedComplete(message.getId(), message.getQos());
        } catch (MqttException e) {
            log.warn("PUBACK fuer Nachricht {} fehlgeschlagen, Broker stellt sie erneut zu: {}",
                    message.getId(), e.getMessage());
        }
    }

    private final class Callback implements MqttCallback {

        @Override
        public void connectComplete(boolean reconnect, String serverURI) {
            log.info("MQTT {} mit {} als {}", reconnect ? "wieder verbunden" : "verbunden", serverURI, settings.clientId());
            subscribeAll();
            try {
                onConnect.run();
            } catch (RuntimeException e) {
                log.error("onConnect-Aktion fehlgeschlagen: {}", e.getMessage(), e);
            }
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) {
            worker.execute(() -> handle(topic, message));
        }

        @Override
        public void disconnected(MqttDisconnectResponse response) {
            log.warn("MQTT-Verbindung verloren: {}", response.getReasonString());
        }

        @Override
        public void mqttErrorOccurred(MqttException exception) {
            log.error("MQTT-Fehler: {}", exception.getMessage(), exception);
        }

        @Override
        public void deliveryComplete(IMqttToken token) {
        }

        @Override
        public void authPacketArrived(int reasonCode, MqttProperties properties) {
        }
    }
}
