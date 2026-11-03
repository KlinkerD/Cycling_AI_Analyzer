package com.cycling.ai;

import com.cycling.messaging.MessagePublisher;
import com.cycling.messaging.Topics;
import com.cycling.messaging.contract.AiRequest;
import com.cycling.messaging.contract.AiResult;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiRequestHandlerTest {

    private static final String FROM_REPORT = Topics.aiRequest("report");

    private final GeminiClient gemini = mock(GeminiClient.class);
    private final MessagePublisher publisher = mock(MessagePublisher.class);
    private final List<Duration> sleeps = new ArrayList<>();
    private final AiRequestHandler handler = new AiRequestHandler(gemini, publisher, Duration.ofSeconds(5),
            Clock.fixed(Instant.parse("2026-11-02T10:00:00Z"), ZoneOffset.UTC), sleeps::add);

    private AiResult published(String source) {
        ArgumentCaptor<AiResult> captor = ArgumentCaptor.forClass(AiResult.class);
        verify(publisher).publish(eq(Topics.aiResult(source)), captor.capture());
        return captor.getValue();
    }

    @Test
    void passesPromptAndSettingsThroughUnchanged() {
        when(gemini.generate("Prompt", 0.3, true)).thenReturn("{\"score\": 8}");

        handler.handle(FROM_REPORT, new AiRequest("job-42", "Prompt", 0.3, true));

        assertThat(published("report")).isEqualTo(AiResult.ok("job-42", "{\"score\": 8}"));
    }

    @Test
    void answerGoesToTheTopicOfTheRequestingSource() {
        when(gemini.generate(anyString(), anyDouble(), eq(false))).thenReturn("Fahr heute locker.");

        handler.handle(Topics.aiRequest("training"), new AiRequest("rec-1", "Prompt", 0.5, false));

        assertThat(published("training").text()).isEqualTo("Fahr heute locker.");
    }

    @Test
    void geminiFailureIsAnsweredWithErrorResult() {
        when(gemini.generate(anyString(), anyDouble(), anyBoolean()))
                .thenThrow(new GeminiClient.GeminiException("HTTP 429", null));

        handler.handle(FROM_REPORT, new AiRequest("job-1", "Prompt", 0.3, true));

        AiResult result = published("report");
        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("HTTP 429");
        assertThat(result.text()).isNull();
    }

    @Test
    void blankPromptIsRejectedWithoutCallingGemini() {
        handler.handle(FROM_REPORT, new AiRequest("job-1", " ", 0.3, false));

        assertThat(published("report").success()).isFalse();
        verify(gemini, never()).generate(anyString(), anyDouble(), anyBoolean());
    }

    @Test
    void temperatureOutsideRangeIsRejected() {
        handler.handle(FROM_REPORT, new AiRequest("job-1", "Prompt", 2.5, false));

        assertThat(published("report").error()).contains("Temperatur");
        verify(gemini, never()).generate(anyString(), anyDouble(), anyBoolean());
    }

    @Test
    void secondCallWaitsForMinInterval() {
        when(gemini.generate(anyString(), anyDouble(), anyBoolean())).thenReturn("ok");

        handler.handle(FROM_REPORT, new AiRequest("a", "Prompt", 0.3, false));
        handler.handle(FROM_REPORT, new AiRequest("b", "Prompt", 0.3, false));

        assertThat(sleeps).containsExactly(Duration.ofSeconds(5));
    }
}
