package com.cycling.report.ftp;

import com.cycling.messaging.MessagePublisher;
import com.cycling.messaging.MessagingException;
import com.cycling.messaging.Topics;
import com.cycling.messaging.contract.DetectFtpRequest;
import com.cycling.messaging.contract.DetectFtpResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FtpDetectionServiceTest {

    private final Map<String, FtpDetection> store = new HashMap<>();
    private final FtpDetectionRepository repository = mock(FtpDetectionRepository.class);
    private final MessagePublisher publisher = mock(MessagePublisher.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-05T10:00:00Z"));
    private final FtpDetectionService service =
            new FtpDetectionService(repository, publisher, clock, Duration.ofMinutes(2));

    FtpDetectionServiceTest() {
        when(repository.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(store.get(inv.<String>getArgument(0))));
        when(repository.save(any())).thenAnswer(inv -> {
            FtpDetection d = inv.getArgument(0);
            store.put(d.getActivityId(), d);
            return d;
        });
    }

    private DetectFtpRequest sentRequest() {
        ArgumentCaptor<DetectFtpRequest> captor = ArgumentCaptor.forClass(DetectFtpRequest.class);
        verify(publisher).publish(eq(Topics.DETECT_FTP_REQUEST), captor.capture());
        return captor.getValue();
    }

    @Test
    void startSendsRequestAndStoresPendingDetection() {
        FtpDetection detection = service.start("i1");

        DetectFtpRequest request = sentRequest();
        assertThat(request.activityId()).isEqualTo("i1");
        assertThat(request.requestId()).isEqualTo(detection.getRequestId());
        assertThat(store.get("i1").getStatus()).isEqualTo(FtpDetectionStatus.PENDING);
    }

    @Test
    void secondStartWhilePendingSendsNothingNew() {
        FtpDetection first = service.start("i1");
        FtpDetection second = service.start("i1");

        assertThat(second.getRequestId()).isEqualTo(first.getRequestId());
        verify(publisher, times(1)).publish(eq(Topics.DETECT_FTP_REQUEST), any());
    }

    @Test
    void detectedResponseStoresValues() {
        String requestId = service.start("i1").getRequestId();

        service.complete(new DetectFtpResponse(requestId, "i1", true, 266, 182, 172, 280, 1200, null));

        FtpDetection d = store.get("i1");
        assertThat(d.getStatus()).isEqualTo(FtpDetectionStatus.DETECTED);
        assertThat(d.getFtp()).isEqualTo(266);
        assertThat(d.getMaxHr()).isEqualTo(182);
        assertThat(d.getLthr()).isEqualTo(172);
        assertThat(d.getAvgPower20Min()).isEqualTo(280);
        assertThat(d.getWindowStartSeconds()).isEqualTo(1200);
        assertThat(d.getFinishedAt()).isNotNull();
    }

    @Test
    void notDetectedResponseIsNoError() {
        String requestId = service.start("i1").getRequestId();

        service.complete(DetectFtpResponse.notDetected(requestId, "i1"));

        assertThat(store.get("i1").getStatus()).isEqualTo(FtpDetectionStatus.NOT_DETECTED);
        assertThat(store.get("i1").getError()).isNull();
    }

    @Test
    void errorResponseMarksFailed() {
        String requestId = service.start("i1").getRequestId();

        service.complete(DetectFtpResponse.failed(requestId, "i1", "report-service nicht erreichbar"));

        assertThat(store.get("i1").getStatus()).isEqualTo(FtpDetectionStatus.FAILED);
        assertThat(store.get("i1").getError()).isEqualTo("report-service nicht erreichbar");
    }

    @Test
    void responseOfOldRunIsDiscarded() {
        String oldRequest = service.start("i1").getRequestId();
        service.complete(DetectFtpResponse.failed(oldRequest, "i1", "kaputt"));
        String newRequest = service.start("i1").getRequestId();

        service.complete(new DetectFtpResponse(oldRequest, "i1", true, 1, 1, 1, 1, 1, null));

        FtpDetection d = store.get("i1");
        assertThat(d.getRequestId()).isEqualTo(newRequest);
        assertThat(d.getStatus()).isEqualTo(FtpDetectionStatus.PENDING);
    }

    @Test
    void restartAfterResultOverwritesOldValues() {
        String first = service.start("i1").getRequestId();
        service.complete(new DetectFtpResponse(first, "i1", true, 266, 182, 172, 280, 1200, null));

        FtpDetection restarted = service.start("i1");

        assertThat(restarted.getStatus()).isEqualTo(FtpDetectionStatus.PENDING);
        assertThat(restarted.getFtp()).isNull();
        assertThat(restarted.getRequestId()).isNotEqualTo(first);
    }

    @Test
    void pendingRunExpiresAfterTimeoutWhenRead() {
        service.start("i1");
        clock.advance(Duration.ofMinutes(3));

        FtpDetection d = service.find("i1").orElseThrow();

        assertThat(d.getStatus()).isEqualTo(FtpDetectionStatus.FAILED);
        assertThat(d.getError()).contains("ftp-service");
    }

    @Test
    void expiredRunCanBeStartedAgain() {
        service.start("i1");
        clock.advance(Duration.ofMinutes(3));

        FtpDetection d = service.start("i1");

        assertThat(d.getStatus()).isEqualTo(FtpDetectionStatus.PENDING);
        verify(publisher, times(2)).publish(eq(Topics.DETECT_FTP_REQUEST), any());
    }

    @Test
    void publishFailureMarksFailed() {
        doThrow(new MessagingException("Broker weg", null)).when(publisher).publish(any(), any());

        FtpDetection d = service.start("i1");

        assertThat(d.getStatus()).isEqualTo(FtpDetectionStatus.FAILED);
        assertThat(d.getError()).contains("Broker weg");
    }

    @Test
    void responseForUnknownActivityIsIgnored() {
        service.complete(DetectFtpResponse.notDetected("r1", "unbekannt"));

        assertThat(store).isEmpty();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
