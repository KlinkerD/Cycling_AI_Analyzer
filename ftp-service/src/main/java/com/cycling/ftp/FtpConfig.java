package com.cycling.ftp;

import com.cycling.intervals.client.IntervalsClient;
import com.cycling.messaging.MqttMessaging;
import com.cycling.messaging.MqttSettings;
import com.cycling.messaging.Topics;
import com.cycling.messaging.contract.DetectFtpRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.time.Duration;

/**
 * Verdrahtet den ftp-service: MQTT rein ({@code DetectFtpRequest}), REST zum intervals-service (Streams),
 * MQTT raus ({@code DetectFtpResponse}). Kein Webserver, kein Speicher.
 */
@Configuration
public class FtpConfig {

    @Bean(destroyMethod = "close")
    public MqttMessaging mqttMessaging(
            @Value("${mqtt.broker-url}") String brokerUrl,
            @Value("${mqtt.client-id}") String clientId,
            @Value("${mqtt.username:}") String username,
            @Value("${mqtt.password:}") String password,
            @Value("${mqtt.session-expiry:1d}") Duration sessionExpiry) {
        return new MqttMessaging(new MqttSettings(brokerUrl, clientId, username, password, sessionExpiry));
    }

    @Bean
    public IntervalsStreamsClient intervalsStreamsClient(
            @Value("${intervals-service.base-url}") String baseUrl,
            @Value("${intervals-service.connect-timeout:5s}") Duration connectTimeout,
            @Value("${intervals-service.read-timeout:60s}") Duration readTimeout) {
        return new IntervalsStreamsClient(IntervalsClient.create(baseUrl, connectTimeout, readTimeout));
    }

    @Bean
    public FtpDetector ftpDetector(
            @Value("${ftp.detection.window-seconds:1200}") int windowSeconds,
            @Value("${ftp.detection.max-missing-fraction:0.05}") double maxMissingFraction,
            @Value("${ftp.detection.min-rest-ratio:1.25}") double minRestRatio,
            @Value("${ftp.detection.final-seconds:300}") int finalSeconds,
            @Value("${ftp.detection.min-final-hr-fraction:0.90}") double minFinalHrFraction,
            @Value("${ftp.detection.ftp-factor:0.95}") double ftpFactor,
            @Value("${ftp.detection.max-hr-smoothing-seconds:5}") int maxHrSmoothingSeconds) {
        return new FtpDetector(new FtpDetectionSettings(windowSeconds, maxMissingFraction, minRestRatio,
                finalSeconds, minFinalHrFraction, ftpFactor, maxHrSmoothingSeconds));
    }

    @Bean
    public FtpRequestHandler ftpRequestHandler(IntervalsStreamsClient streamsClient, FtpDetector detector,
                                               MqttMessaging messaging) {
        FtpRequestHandler handler = new FtpRequestHandler(streamsClient, detector, messaging);
        messaging.subscribe(Topics.DETECT_FTP_REQUEST, DetectFtpRequest.class, handler::handle);
        return handler;
    }

    /** Erst verbinden, wenn alle Handler registriert sind. */
    @EventListener(ApplicationReadyEvent.class)
    public void connectMqtt(ApplicationReadyEvent event) {
        event.getApplicationContext().getBean(MqttMessaging.class).start();
    }
}
