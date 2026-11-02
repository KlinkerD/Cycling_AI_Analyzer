package com.cycling.ai;

import com.cycling.messaging.MessagePublisher;
import com.cycling.messaging.Topics;
import com.cycling.messaging.contract.AnalysisRequest;
import com.cycling.messaging.contract.AnalysisResult;
import com.cycling.messaging.contract.RecommendationTextRequest;
import com.cycling.messaging.contract.RecommendationTextResult;
import com.cycling.messaging.contract.WorkoutData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiRequestHandlerTest {

    private final GeminiClient gemini = mock(GeminiClient.class);
    private final MessagePublisher publisher = mock(MessagePublisher.class);
    private final List<Duration> sleeps = new ArrayList<>();
    private final AiRequestHandler handler = new AiRequestHandler(gemini, new PromptBuilder(), publisher,
            new ObjectMapper(), Duration.ofSeconds(5),
            Clock.fixed(Instant.parse("2026-11-02T10:00:00Z"), ZoneOffset.UTC), sleeps::add);

    private static AnalysisRequest request(String id) {
        WorkoutData ride = new WorkoutData("Runde", null, false, 30.0, 3600, 200.0, null,
                null, null, null, null, null, 250, 0.8, null);
        return new AnalysisRequest(id, ride, List.of(), List.of(), List.of(), 250, 190);
    }

    private AnalysisResult publishedAnalysis() {
        ArgumentCaptor<AnalysisResult> captor = ArgumentCaptor.forClass(AnalysisResult.class);
        verify(publisher).publish(eq(Topics.AI_ANALYSIS_RESULT), captor.capture());
        return captor.getValue();
    }

    @Test
    void publishesParsedAnalysisWithSameRequestId() {
        when(gemini.generate(anyString(), anyDouble(), eq(true))).thenReturn("""
                ```json
                {"score": 8, "strengths": "s", "anomalies": "a", "progress": "p", "summary": "z"}
                ```""");

        handler.handleAnalysis(request("job-42"));

        AnalysisResult result = publishedAnalysis();
        assertThat(result.requestId()).isEqualTo("job-42");
        assertThat(result.success()).isTrue();
        assertThat(result.score()).isEqualTo(8);
        assertThat(result.summary()).isEqualTo("z");
    }

    @Test
    void geminiFailureIsAnsweredWithErrorResult() {
        when(gemini.generate(anyString(), anyDouble(), anyBoolean()))
                .thenThrow(new GeminiClient.GeminiException("HTTP 429", null));

        handler.handleAnalysis(request("job-1"));

        AnalysisResult result = publishedAnalysis();
        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("HTTP 429");
    }

    @Test
    void invalidJsonIsAnsweredWithErrorResult() {
        when(gemini.generate(anyString(), anyDouble(), anyBoolean())).thenReturn("Das ist kein JSON");

        handler.handleAnalysis(request("job-1"));

        assertThat(publishedAnalysis().success()).isFalse();
    }

    @Test
    void secondCallWaitsForMinInterval() {
        when(gemini.generate(anyString(), anyDouble(), anyBoolean())).thenReturn("{\"score\":7}");

        handler.handleAnalysis(request("a"));
        handler.handleAnalysis(request("b"));

        assertThat(sleeps).containsExactly(Duration.ofSeconds(5));
    }

    @Test
    void recommendationTextIsPublished() {
        when(gemini.generate(anyString(), anyDouble(), eq(false))).thenReturn("Fahr heute locker.");

        handler.handleRecommendation(new RecommendationTextRequest("rec-1", "Locker", -15, 2, false, ""));

        ArgumentCaptor<RecommendationTextResult> captor = ArgumentCaptor.forClass(RecommendationTextResult.class);
        verify(publisher).publish(eq(Topics.AI_RECOMMENDATION_RESULT), captor.capture());
        assertThat(captor.getValue()).isEqualTo(
                new RecommendationTextResult("rec-1", true, null, "Fahr heute locker."));
    }
}
