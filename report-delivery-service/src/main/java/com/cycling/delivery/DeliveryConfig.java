package com.cycling.delivery;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Verdrahtet den Report-Versand. Beide Kanaele sind per Default aus und werden
 * unabhaengig ueber cycling.report.mail.enabled / cycling.report.discord.enabled eingeschaltet.
 */
@Configuration
public class DeliveryConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public Sleeper sleeper() {
        return Thread::sleep;
    }

    @Bean
    public MailReportSender mailReportSender(
            JavaMailSender mailSender,
            @Value("${cycling.report.mail.enabled:false}") boolean enabled,
            @Value("${cycling.report.mail.from:}") String from,
            @Value("${spring.mail.username:}") String smtpUsername,
            @Value("${cycling.report.mail.from-name:Cycling Report}") String fromName,
            @Value("${cycling.report.mail.to:}") String to,
            @Value("${cycling.report.mail.subject-prefix:[Cycling Report]}") String subjectPrefix) {
        return new MailReportSender(new MailSettings(enabled, from, smtpUsername, fromName, to, subjectPrefix), mailSender);
    }

    @Bean
    public DiscordReportSender discordReportSender(
            Sleeper sleeper,
            @Value("${cycling.report.discord.enabled:false}") boolean enabled,
            @Value("${cycling.report.discord.bot-token:}") String botToken,
            @Value("${cycling.report.discord.channel-id:}") String channelId,
            @Value("${cycling.report.discord.api-base-url:" + DiscordSettings.DEFAULT_API_BASE_URL + "}") String apiBaseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(30));
        RestClient restClient = RestClient.builder().requestFactory(requestFactory).build();
        return new DiscordReportSender(new DiscordSettings(enabled, botToken, channelId, apiBaseUrl), restClient, sleeper);
    }

    @Bean
    public ReportDeliveryService reportDeliveryService(List<ReportSender> senders,
                                                       ReportDeliveryRepository repository,
                                                       ReportChartRenderer renderer,
                                                       Clock clock) {
        return new ReportDeliveryService(senders, repository, renderer, clock);
    }

    /** Laedt Reports beim analysis-service; grosszuegiger Timeout, weil dort Streams von intervals.icu geholt werden. */
    @Bean
    public ReportClient reportClient(@Value("${analysis-service.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(90));
        return new ReportClient(RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build());
    }

    /** Eigener Single-Thread-Executor: Versand, Retry und manuelles Senden laufen nie parallel. */
    @Bean
    public ReportDispatcher reportDispatcher(ReportDeliveryService deliveryService, ReportClient reportClient) {
        return new ReportDispatcher(deliveryService, reportClient::fetch,
                Executors.newSingleThreadExecutor(Thread.ofPlatform().name("report-delivery").daemon().factory()));
    }
}
