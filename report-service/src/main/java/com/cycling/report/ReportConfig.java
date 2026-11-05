package com.cycling.report;

import com.cycling.intervals.client.IntervalsClient;
import com.cycling.messaging.MqttMessaging;
import com.cycling.messaging.MqttSettings;
import com.cycling.messaging.Topics;
import com.cycling.messaging.contract.ActivityDetected;
import com.cycling.messaging.contract.AiResult;
import com.cycling.messaging.contract.DeliveryServiceStatus;
import com.cycling.messaging.contract.DetectFtpResponse;
import com.cycling.report.analysis.ActivityAnalysisRepository;
import com.cycling.report.analysis.ActivityDetectedHandler;
import com.cycling.report.analysis.AnalysisJobRepository;
import com.cycling.report.analysis.AnalysisJobService;
import com.cycling.report.analysis.AnalysisService;
import com.cycling.report.analysis.IntervalsData;
import com.cycling.report.analysis.ReportResendService;
import com.cycling.report.ftp.FtpDetectionRepository;
import com.cycling.report.ftp.FtpDetectionService;
import com.cycling.report.map.HttpTileSource;
import com.cycling.report.map.RouteMapRenderer;
import com.cycling.report.map.TileSource;
import com.cycling.report.weather.RideWeatherService;
import com.cycling.report.weather.WeatherPointRepository;
import com.cycling.weather.client.WeatherClient;
import com.cycling.web.CyclingWebConfig;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Verdrahtet den report-service: REST zum intervals-service, MQTT rein (neue Aktivitaeten, KI-Ergebnisse,
 * Status des delivery-service), MQTT raus (KI-Auftraege, DeliveryMessage), REST-API fuer das Frontend.
 */
@Configuration
@Import(CyclingWebConfig.class)
public class ReportConfig {

    /** Abstaende der Wiederholungen nach einem Fehlschlag beim Versand (danach aufgeben). */
    static final List<Duration> RETRY_DELAYS = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15));

    @Bean
    public OpenAPI reportOpenApi() {
        return CyclingWebConfig.openApi("Cycling Report API");
    }

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean(destroyMethod = "close")
    public MqttMessaging mqttMessaging(
            @Value("${mqtt.broker-url}") String brokerUrl,
            @Value("${mqtt.client-id}") String clientId,
            @Value("${mqtt.username:}") String username,
            @Value("${mqtt.password:}") String password,
            @Value("${mqtt.session-expiry:1d}") Duration sessionExpiry) {
        return new MqttMessaging(new MqttSettings(brokerUrl, clientId, username, password, sessionExpiry));
    }

    /**
     * Daten vom intervals-service. Lese-Timeout grosszuegig, weil der Service bei Rate-Limits von
     * intervals.icu selbst ein paar Sekunden wartet und wiederholt; hier wird nicht wiederholt.
     */
    @Bean
    public IntervalsData intervalsData(@Value("${intervals-service.base-url}") String baseUrl,
                                       @Value("${intervals-service.connect-timeout:5s}") Duration connectTimeout,
                                       @Value("${intervals-service.read-timeout:60s}") Duration readTimeout) {
        return new IntervalsData(IntervalsClient.create(baseUrl, connectTimeout, readTimeout));
    }

    /**
     * Wetter der Fahrt vom weather-service. Leere URL = kein Wetter. Kurzer Lese-Timeout: das Wetter ist ein
     * Extra, die Analyse soll nicht darauf warten (der Client wiederholt einmal).
     */
    @Bean
    public RideWeatherService rideWeatherService(@Value("${weather-service.base-url:}") String baseUrl,
                                                 @Value("${weather-service.connect-timeout:3s}") Duration connectTimeout,
                                                 @Value("${weather-service.read-timeout:10s}") Duration readTimeout,
                                                 WeatherPointRepository repository) {
        WeatherClient client = baseUrl.isBlank() ? null : WeatherClient.create(baseUrl, connectTimeout, readTimeout);
        return new RideWeatherService(client, repository);
    }

    // ---- Versand --------------------------------------------------------------------------------

    /** Kartenbild fuer den Versand; leere Kachel-URL = nur die Route auf neutralem Grund. */
    @Bean
    public RouteMapRenderer routeMapRenderer(@Value("${report.map.tile-url:}") String tileUrl,
                                             @Value("${report.map.user-agent:cycling-ai-analyzer}") String userAgent,
                                             @Value("${report.map.attribution:}") String attribution) {
        TileSource tiles = tileUrl.isBlank() ? TileSource.none() : new HttpTileSource(tileUrl, userAgent, attribution);
        return new RouteMapRenderer(tiles);
    }

    @Bean
    public ReportMessageBuilder reportMessageBuilder(@Value("${report.targets:}") String targets) {
        return new ReportMessageBuilder(ReportTargets.parse(targets));
    }

    /** Ein Thread: Auftraege und Wiederholungen laufen nacheinander. */
    @Bean(destroyMethod = "shutdownNow")
    public ScheduledExecutorService reportExecutor() {
        return Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("report").daemon().factory());
    }

    @Bean
    public ReportRequestHandler reportRequestHandler(ReportChartRenderer renderer,
                                                     ReportMessageBuilder messageBuilder, MqttMessaging messaging,
                                                     ScheduledExecutorService reportExecutor) {
        ReportRequestHandler.Scheduler scheduler = (task, delay) ->
                reportExecutor.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS);
        return new ReportRequestHandler(renderer, messageBuilder, messaging, scheduler, RETRY_DELAYS);
    }

    // ---- Analyse --------------------------------------------------------------------------------

    @Bean
    public AnalysisJobService analysisJobService(AnalysisService analysisService,
                                                 AnalysisJobRepository jobRepository,
                                                 MqttMessaging messaging,
                                                 ReportRequestHandler reportRequestHandler,
                                                 Clock clock,
                                                 @Value("${analysis.job-timeout:15m}") Duration timeout) {
        // Eigener Mapper nur fuer das im Job gespeicherte ActivityDto (unbekannte Felder alter Jobs ignorieren).
        ObjectMapper jobJson = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return new AnalysisJobService(analysisService, jobRepository, messaging, reportRequestHandler::handle,
                jobJson, clock, timeout);
    }

    @Bean
    public ReportResendService reportResendService(ActivityAnalysisRepository repository,
                                                   AnalysisService analysisService,
                                                   ReportRequestHandler reportRequestHandler) {
        return new ReportResendService(repository, analysisService, reportRequestHandler::handle);
    }

    @Bean
    public ActivityDetectedHandler activityDetectedHandler(AnalysisJobService jobService,
                                                           ActivityAnalysisRepository repository) {
        return new ActivityDetectedHandler(jobService, repository);
    }

    // ---- FTP-Test-Erkennung ---------------------------------------------------------------------

    @Bean
    public FtpDetectionService ftpDetectionService(FtpDetectionRepository repository, MqttMessaging messaging,
                                                   Clock clock,
                                                   @Value("${ftp-detection.timeout:2m}") Duration timeout) {
        return new FtpDetectionService(repository, messaging, clock, timeout);
    }

    /** Handler registrieren und erst danach verbinden, damit keine nachgelieferte Nachricht verloren geht. */
    @EventListener(ApplicationReadyEvent.class)
    public void connectMqtt(ApplicationReadyEvent event) {
        var context = event.getApplicationContext();
        MqttMessaging messaging = context.getBean(MqttMessaging.class);
        AnalysisJobService jobs = context.getBean(AnalysisJobService.class);
        ReportResendService resend = context.getBean(ReportResendService.class);
        ActivityDetectedHandler detected = context.getBean(ActivityDetectedHandler.class);
        FtpDetectionService ftp = context.getBean(FtpDetectionService.class);

        messaging.subscribe(Topics.INTERVALS_ACTIVITY_DETECTED, ActivityDetected.class, detected::onActivityDetected);
        messaging.subscribe(Topics.aiResult(AnalysisJobService.AI_SOURCE), AiResult.class, jobs::complete);
        messaging.subscribe(Topics.DELIVERY_STATUS, DeliveryServiceStatus.class, resend::updateStatus);
        messaging.subscribe(Topics.DETECT_FTP_RESPONSE, DetectFtpResponse.class, ftp::complete);
        messaging.start();
    }
}
