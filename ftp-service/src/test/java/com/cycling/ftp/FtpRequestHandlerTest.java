package com.cycling.ftp;

import com.cycling.types.ActivityId;
import java.util.UUID;
import com.cycling.messaging.MessagePublisher;
import com.cycling.messaging.Topics;
import com.cycling.messaging.contract.DetectFtpRequest;
import com.cycling.messaging.contract.DetectFtpResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FtpRequestHandlerTest {

    private static final UUID R1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final ActivityId I1 = ActivityId.of("i1");

    private static final StreamData STREAMS = new StreamData(List.of(0), List.of(200), List.of(150));

    private final IntervalsStreamsClient client = mock(IntervalsStreamsClient.class);
    private final FtpDetector detector = mock(FtpDetector.class);
    private final MessagePublisher publisher = mock(MessagePublisher.class);
    private final FtpRequestHandler handler = new FtpRequestHandler(client, detector, publisher);

    private DetectFtpResponse published() {
        ArgumentCaptor<DetectFtpResponse> captor = ArgumentCaptor.forClass(DetectFtpResponse.class);
        verify(publisher).publish(eq(Topics.DETECT_FTP_RESPONSE), captor.capture());
        return captor.getValue();
    }

    @Test
    void publishesDetectedValues() {
        when(client.streams(I1)).thenReturn(STREAMS);
        when(detector.detect(STREAMS)).thenReturn(FtpTestResult.detected(266, 182, 171, 280, 1200));

        handler.handle(new DetectFtpRequest(R1, I1));

        assertThat(published()).isEqualTo(
                new DetectFtpResponse(R1, I1, true, 266, 182, 171, 280, 1200, null));
    }

    @Test
    void publishesNotDetectedWithoutError() {
        when(client.streams(I1)).thenReturn(STREAMS);
        when(detector.detect(STREAMS)).thenReturn(FtpTestResult.notDetected("kein Test"));

        handler.handle(new DetectFtpRequest(R1, I1));

        assertThat(published()).isEqualTo(DetectFtpResponse.notDetected(R1, I1));
    }

    @Test
    void publishesErrorWhenStreamsAreUnavailable() {
        when(client.streams(I1)).thenThrow(new IllegalStateException("intervals-service nicht erreichbar"));

        handler.handle(new DetectFtpRequest(R1, I1));

        DetectFtpResponse response = published();
        assertThat(response.isFtpDetected()).isFalse();
        assertThat(response.error()).isEqualTo("intervals-service nicht erreichbar");
        assertThat(response.requestId()).isEqualTo(R1);
    }

    @Test
    void rejectsRequestWithoutActivityId() {
        handler.handle(new DetectFtpRequest(R1, null));

        assertThat(published().error()).contains("activityId");
        verifyNoInteractions(client, detector);
    }

    @Test
    void detectorFailureBecomesError() {
        when(client.streams(any())).thenReturn(STREAMS);
        when(detector.detect(STREAMS)).thenThrow(new IllegalArgumentException("kaputt"));

        handler.handle(new DetectFtpRequest(R1, I1));

        assertThat(published().error()).isEqualTo("kaputt");
    }
}
