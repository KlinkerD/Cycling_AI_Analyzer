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
    private final Map<String, Consumer<byte[]>> handlers = new ConcurrentHashMap<>();
    private final MqttAsyncClient client;
    private final ExecutorService worker =
            Executors.newSingleThreadExecutor(Thread.ofPlatform().name("mqtt-worker").factory());
    private final ScheduledExecutorService connector =
            Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("mqtt-connect").daemon().factory());
    private volatile boolean closed;

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

    /** Registriert einen Handler fuer genau dieses Topic (keine Wildcards). */
    public <T> void subscribe(String topic, Class<T> type, Consumer<T> handler) {
        handlers.put(topic, payload -> handler.accept(codec.decode(payload, type)));
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
        Consumer<byte[]> handler = handlers.get(topic);
        try {
            if (handler == null) {
                log.warn("Kein Handler fuer Topic {}, Nachricht verworfen", topic);
            } else {
                handler.accept(message.getPayload());
            }
        } catch (RuntimeException e) {
            // Nicht erneut zustellen lassen: eine kaputte Nachricht soll die Queue nicht blockieren.
            log.error("Nachricht auf {} konnte nicht verarbeitet werden: {}", topic, e.getMessage(), e);
        } finally {
            acknowledge(message);
        }
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
