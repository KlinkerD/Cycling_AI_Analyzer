package com.cycling.ai;

import com.cycling.messaging.MessagePublisher;
import com.cycling.messaging.Topics;
import com.cycling.messaging.contract.AiRequest;
import com.cycling.messaging.contract.AiResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Beantwortet KI-Auftraege: Prompt rein, Antworttext raus. Der Handler kennt keine Fachlichkeit; die
 * Absender bauen den Prompt und werten die Antwort selbst aus.
 *
 * <p>Jeder Auftrag bekommt genau eine Antwort (Erfolg oder Fehler) auf {@code Topics.aiResult(quelle)},
 * damit der Absender nicht auf ein Timeout warten muss. Die Aufrufe laufen nacheinander (ein
 * MQTT-Worker-Thread) mit einem Mindestabstand fuer das Gemini-Rate-Limit.
 */
public class AiRequestHandler {

    private static final Logger log = LoggerFactory.getLogger(AiRequestHandler.class);
    /** Obergrenze der Gemini-Temperatur. */
    static final double MAX_TEMPERATURE = 2.0;

    private final GeminiClient gemini;
    private final MessagePublisher publisher;
    private final Duration minInterval;
    private final Clock clock;
    private final Sleeper sleeper;
    private Instant lastCall = Instant.MIN;

    public AiRequestHandler(GeminiClient gemini, MessagePublisher publisher, Duration minInterval, Clock clock,
                            Sleeper sleeper) {
        this.gemini = gemini;
        this.publisher = publisher;
        this.minInterval = minInterval;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    /** @param topic konkretes Topic des Auftrags; der letzte Teil ist die Quelle, an die geantwortet wird */
    public void handle(String topic, AiRequest request) {
        String source = Topics.source(topic);
        log.info("KI-Auftrag {} von {} ({} Zeichen, json={})", request.requestId(), source,
                request.prompt() == null ? 0 : request.prompt().length(), request.json());
        AiResult result;
        try {
            validate(request);
            throttle();
            String text = gemini.generate(request.prompt(), request.temperature(), request.json());
            result = AiResult.ok(request.requestId(), text);
            log.info("KI-Auftrag {} von {} fertig", request.requestId(), source);
        } catch (RuntimeException e) {
            log.error("KI-Auftrag {} von {} fehlgeschlagen: {}", request.requestId(), source, e.getMessage());
            result = AiResult.failed(request.requestId(), e.getMessage());
        }
        publisher.publish(Topics.aiResult(source), result);
    }

    private static void validate(AiRequest request) {
        if (request.prompt() == null || request.prompt().isBlank()) {
            throw new IllegalArgumentException("Auftrag ohne Prompt");
        }
        if (request.temperature() < 0 || request.temperature() > MAX_TEMPERATURE) {
            throw new IllegalArgumentException(
                    "Temperatur " + request.temperature() + " ausserhalb 0.." + MAX_TEMPERATURE);
        }
    }

    private void throttle() {
        Instant now = clock.instant();
        Instant earliest = lastCall == Instant.MIN ? now : lastCall.plus(minInterval);
        if (earliest.isAfter(now)) {
            try {
                sleeper.sleep(Duration.between(now, earliest));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Warten auf Gemini-Rate-Limit unterbrochen", e);
            }
        }
        lastCall = clock.instant();
    }

    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }
}
