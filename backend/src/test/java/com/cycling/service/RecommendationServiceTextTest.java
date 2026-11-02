package com.cycling.service;

import com.cycling.dto.DailyRecommendationDto;
import com.cycling.dto.WellnessPointDto;
import com.cycling.messaging.MessagePublisher;
import com.cycling.messaging.MessagingException;
import com.cycling.messaging.Topics;
import com.cycling.messaging.contract.RecommendationTextRequest;
import com.cycling.messaging.contract.RecommendationTextResult;
import com.cycling.model.DailyRecommendation;
import com.cycling.model.RecommendationCategory;
import com.cycling.repository.ActivityAnalysisRepository;
import com.cycling.repository.DailyRecommendationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Kategorie und Fallback kommen sofort, der KI-Text wird per MQTT nachgeliefert. */
class RecommendationServiceTextTest {

    private final IntervalsService intervals = mock(IntervalsService.class);
    private final MessagePublisher publisher = mock(MessagePublisher.class);
    private final DailyRecommendationRepository repository = mock(DailyRecommendationRepository.class);
    private final ActivityAnalysisRepository activities = mock(ActivityAnalysisRepository.class);
    private final AtomicReference<DailyRecommendation> stored = new AtomicReference<>();
    private final RecommendationService service =
            new RecommendationService(intervals, publisher, repository, activities);

    @BeforeEach
    void setUp() {
        when(repository.save(any())).thenAnswer(inv -> {
            stored.set(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(repository.findTopByOrderByIdAsc()).thenAnswer(inv -> Optional.ofNullable(stored.get()));
        String today = LocalDate.now().toString();
        when(intervals.getWellness(anyString(), anyString()))
                .thenReturn(List.of(new WellnessPointDto(today, 60.0, 50.0, 10.0)));
        when(activities.findByActivityDateAfterOrderByActivityDateDesc(any())).thenReturn(List.of());
    }

    private String sentRequestId() {
        ArgumentCaptor<RecommendationTextRequest> captor = ArgumentCaptor.forClass(RecommendationTextRequest.class);
        verify(publisher).publish(eq(Topics.AI_RECOMMENDATION_REQUEST), captor.capture());
        return captor.getValue().requestId();
    }

    @Test
    void computeStoresFallbackAndRequestsText() {
        DailyRecommendationDto dto = service.computeAndStore();

        assertThat(dto.textPending()).isTrue();
        assertThat(stored.get().isFallbackUsed()).isTrue();
        assertThat(stored.get().getPendingTextRequestId()).isEqualTo(sentRequestId());
    }

    @Test
    void matchingTextReplacesFallback() {
        service.computeAndStore();

        service.applyText(new RecommendationTextResult(sentRequestId(), true, null, "Heute Tempo!"));

        assertThat(stored.get().getText()).isEqualTo("Heute Tempo!");
        assertThat(stored.get().isFallbackUsed()).isFalse();
        assertThat(DailyRecommendationDto.from(stored.get()).textPending()).isFalse();
    }

    @Test
    void textForOlderComputationIsIgnored() {
        service.computeAndStore();
        String fallback = stored.get().getText();

        service.applyText(new RecommendationTextResult("alt", true, null, "Veraltet"));

        assertThat(stored.get().getText()).isEqualTo(fallback);
        assertThat(stored.get().getPendingTextRequestId()).isNotNull();
    }

    @Test
    void failedTextKeepsFallbackAndStopsWaiting() {
        service.computeAndStore();
        String fallback = stored.get().getText();

        service.applyText(RecommendationTextResult.failed(sentRequestId(), "HTTP 500"));

        assertThat(stored.get().getText()).isEqualTo(fallback);
        assertThat(stored.get().getPendingTextRequestId()).isNull();
    }

    @Test
    void brokerOutageKeepsFallbackWithoutWaiting() {
        doThrow(new MessagingException("nicht verbunden", null)).when(publisher).publish(any(), any());

        DailyRecommendationDto dto = service.computeAndStore();

        assertThat(dto.textPending()).isFalse();
        assertThat(dto.text()).isNotBlank();
    }

    @Test
    void oldPendingRequestIsNotReportedAsPending() {
        DailyRecommendation rec = new DailyRecommendation();
        rec.setCategory(RecommendationCategory.ENDURANCE);
        rec.setPendingTextRequestId("x");
        rec.setComputedAt(LocalDateTime.now().minusMinutes(10));

        assertThat(DailyRecommendationDto.from(rec).textPending()).isFalse();
    }
}
