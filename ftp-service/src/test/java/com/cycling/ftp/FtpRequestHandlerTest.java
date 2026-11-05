package com.cycling.ftp;

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

    private static final StreamData STREAMS = new StreamData(List.of(0), List.of(200), List.of(150));

    private final ReportStreamsClient client = mock(ReportStreamsClient.class);
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
        when(client.streams("i1")).thenReturn(STREAMS);
        when(detector.detect(STREAMS)).thenReturn(FtpTestResult.detected(266, 182, 171, 280, 1200));

        handler.handle(new DetectFtpRequest("r1", "i1"));

        assertThat(published()).isEqualTo(
                new DetectFtpResponse("r1", "i1", true, 266, 182, 171, 280, 1200, null));
    }

    @Test
    void publishesNotDetectedWithoutError() {
        when(client.streams("i1")).thenReturn(STREAMS);
        when(detector.detect(STREAMS)).thenReturn(FtpTestResult.notDetected("kein Test"));

        handler.handle(new DetectFtpRequest("r1", "i1"));

        assertThat(published()).isEqualTo(DetectFtpResponse.notDetected("r1", "i1"));
    }

    @Test
    void publishesErrorWhenStreamsAreUnavailable() {
        when(client.streams("i1")).thenThrow(new IllegalStateException("report-service nicht erreichbar"));

        handler.handle(new DetectFtpRequest("r1", "i1"));

        DetectFtpResponse response = published();
        assertThat(response.isFtpDetected()).isFalse();
        assertThat(response.error()).isEqualTo("report-service nicht erreichbar");
        assertThat(response.requestId()).isEqualTo("r1");
    }

    @Test
    void rejectsRequestWithoutActivityId() {
        handler.handle(new DetectFtpRequest("r1", " "));

        assertThat(published().error()).contains("activityId");
        verifyNoInteractions(client, detector);
    }

    @Test
    void detectorFailureBecomesError() {
        when(client.streams(any())).thenReturn(STREAMS);
        when(detector.detect(STREAMS)).thenThrow(new IllegalArgumentException("kaputt"));

        handler.handle(new DetectFtpRequest("r1", "i1"));

        assertThat(published().error()).isEqualTo("kaputt");
    }
}
