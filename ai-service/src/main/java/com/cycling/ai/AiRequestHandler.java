package com.cycling.ai;

import com.cycling.messaging.MessagePublisher;
import com.cycling.messaging.Topics;
import com.cycling.messaging.contract.AnalysisRequest;
import com.cycling.messaging.contract.AnalysisResult;
import com.cycling.messaging.contract.RecommendationTextRequest;
import com.cycling.messaging.contract.RecommendationTextResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Beantwortet die KI-Auftraege. Jeder Auftrag bekommt genau eine Antwort (Erfolg oder Fehler),
 * damit der report-service nicht auf ein Timeout warten muss. Die Aufrufe laufen nacheinander
 * (ein MQTT-Worker-Thread) mit einem Mindestabstand fuer das Gemini-Rate-Limit.
 */
public class AiRequestHandler {

    private static final Logger log = LoggerFactory.getLogger(AiRequestHandler.class);

    private final GeminiClient gemini;
    private final PromptBuilder prompts;
    private final MessagePublisher publisher;
    private final ObjectMapper objectMapper;
    private final Duration minInterval;
    private final Clock clock;
    private final Sleeper sleeper;
    private Instant lastCall = Instant.MIN;

    public AiRequestHandler(GeminiClient gemini, PromptBuilder prompts, MessagePublisher publisher,
                            ObjectMapper objectMapper, Duration minInterval, Clock clock, Sleeper sleeper) {
        this.gemini = gemini;
        this.prompts = prompts;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.minInterval = minInterval;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    public void handleAnalysis(AnalysisRequest request) {
        log.info("Analyse-Auftrag {} fuer '{}'", request.requestId(), request.workout().activityName());
        AnalysisResult result;
        try {
            throttle();
            String text = gemini.generate(prompts.analysisPrompt(request), 0.3, true);
            result = parseAnalysis(request.requestId(), text);
            log.info("Analyse-Auftrag {} fertig, Score {}/10", request.requestId(), result.score());
        } catch (RuntimeException e) {
            log.error("Analyse-Auftrag {} fehlgeschlagen: {}", request.requestId(), e.getMessage());
            result = AnalysisResult.failed(request.requestId(), e.getMessage());
        }
        publisher.publish(Topics.AI_ANALYSIS_RESULT, result);
    }

    public void handleRecommendation(RecommendationTextRequest request) {
        log.info("Empfehlungs-Auftrag {} ({})", request.requestId(), request.categoryLabel());
        RecommendationTextResult result;
        try {
            throttle();
            String text = gemini.generate(prompts.recommendationPrompt(request), 0.5, false);
            result = new RecommendationTextResult(request.requestId(), true, null, text);
        } catch (RuntimeException e) {
            log.error("Empfehlungs-Auftrag {} fehlgeschlagen: {}", request.requestId(), e.getMessage());
            result = RecommendationTextResult.failed(request.requestId(), e.getMessage());
        }
        publisher.publish(Topics.AI_RECOMMENDATION_RESULT, result);
    }

    AnalysisResult parseAnalysis(String requestId, String text) {
        String json = text.strip();
        if (json.startsWith("```")) {
            json = json.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").strip();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            return new AnalysisResult(
                    requestId, true, null,
                    node.path("score").asInt(5),
                    node.path("strengths").asText(""),
                    node.path("anomalies").asText(""),
                    node.path("progress").asText(""),
                    node.path("summary").asText(""));
        } catch (IOException e) {
            throw new IllegalStateException("Gemini lieferte kein gueltiges JSON: " + e.getMessage(), e);
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
