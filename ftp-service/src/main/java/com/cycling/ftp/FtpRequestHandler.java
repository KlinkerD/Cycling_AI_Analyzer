package com.cycling.ftp;

import com.cycling.messaging.MessagePublisher;
import com.cycling.messaging.Topics;
import com.cycling.messaging.contract.DetectFtpRequest;
import com.cycling.messaging.contract.DetectFtpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Beantwortet {@link DetectFtpRequest}s: Streams beim report-service holen, FTP-Test erkennen, Ergebnis
 * auf {@link Topics#DETECT_FTP_RESPONSE} veroeffentlichen. Jeder Auftrag bekommt genau eine Antwort,
 * technische Fehler als {@code error}, damit der report-service nicht auf sein Timeout warten muss.
 * Zustandslos und damit unkritisch bei erneuter Zustellung.
 */
public class FtpRequestHandler {

    private static final Logger log = LoggerFactory.getLogger(FtpRequestHandler.class);

    private final ReportStreamsClient streamsClient;
    private final FtpDetector detector;
    private final MessagePublisher publisher;

    public FtpRequestHandler(ReportStreamsClient streamsClient, FtpDetector detector, MessagePublisher publisher) {
        this.streamsClient = streamsClient;
        this.detector = detector;
        this.publisher = publisher;
    }

    public void handle(DetectFtpRequest request) {
        String requestId = request.requestId();
        String activityId = request.activityId();
        log.info("FTP-Erkennung {} fuer Aktivitaet {}", requestId, activityId);
        DetectFtpResponse response;
        try {
            if (activityId == null || activityId.isBlank()) {
                throw new IllegalArgumentException("Auftrag ohne activityId");
            }
            FtpTestResult result = detector.detect(streamsClient.streams(activityId));
            if (result.detected()) {
                log.info("FTP-Test in {} erkannt: FTP {} W, LTHR {} bpm, MaxHR {} bpm", activityId,
                        result.ftp(), result.lthr(), result.maxHr());
                response = new DetectFtpResponse(requestId, activityId, true, result.ftp(), result.maxHr(),
                        result.lthr(), result.avgPower20Min(), result.windowStartSeconds(), null);
            } else {
                log.info("Kein FTP-Test in {}: {}", activityId, result.reason());
                response = DetectFtpResponse.notDetected(requestId, activityId);
            }
        } catch (RuntimeException e) {
            log.error("FTP-Erkennung {} fuer {} fehlgeschlagen: {}", requestId, activityId, e.getMessage());
            response = DetectFtpResponse.failed(requestId, activityId, e.getMessage());
        }
        publisher.publish(Topics.DETECT_FTP_RESPONSE, response);
    }
}
