package com.cycling.report;

import com.cycling.messaging.MqttMessaging;
import com.cycling.messaging.MqttSettings;
import com.cycling.messaging.Topics;
import com.cycling.messaging.contract.ReportDeliveryRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Verdrahtet den report-service: MQTT rein (Report-Auftraege), MQTT raus (DeliveryMessage). */
@Configuration
public class ReportConfig {

    /** Abstaende der Wiederholungen nach einem Fehlschlag (danach aufgeben). */
    static final List<Duration> RETRY_DELAYS = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15));

    @Bean(destroyMethod = "close")
    public MqttMessaging mqttMessaging(
            @Value("${mqtt.broker-url}") String brokerUrl,
            @Value("${mqtt.client-id}") String clientId,
            @Value("${mqtt.username:}") String username,
            @Value("${mqtt.password:}") String password,
            @Value("${mqtt.session-expiry:1d}") Duration sessionExpiry) {
        return new MqttMessaging(new MqttSettings(brokerUrl, clientId, username, password, sessionExpiry));
    }

    /** Laedt Reports beim analysis-service; grosszuegiger Timeout, weil dort Streams von intervals.icu geholt werden. */
    @Bean
    public ReportClient reportClient(@Value("${analysis-service.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(90));
        return new ReportClient(RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build());
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
    public ReportRequestHandler reportRequestHandler(ReportClient reportClient, ReportChartRenderer renderer,
                                                     ReportMessageBuilder messageBuilder, MqttMessaging messaging,
                                                     ScheduledExecutorService reportExecutor) {
        ReportRequestHandler.Scheduler scheduler = (task, delay) ->
                reportExecutor.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS);
        return new ReportRequestHandler(reportClient::fetch, renderer, messageBuilder, messaging, scheduler, RETRY_DELAYS);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void connectMqtt(ApplicationReadyEvent event) {
        var context = event.getApplicationContext();
        MqttMessaging messaging = context.getBean(MqttMessaging.class);
        ReportRequestHandler handler = context.getBean(ReportRequestHandler.class);

        messaging.subscribe(Topics.REPORT_READY, ReportDeliveryRequest.class, handler::handle);
        messaging.subscribe(Topics.REPORT_RESEND, ReportDeliveryRequest.class, handler::handle);
        messaging.start();
    }
}
