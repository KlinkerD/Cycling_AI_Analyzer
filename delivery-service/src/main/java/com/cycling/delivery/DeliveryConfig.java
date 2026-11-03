package com.cycling.delivery;

import com.cycling.messaging.MessageCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Verdrahtet den Versand. Beide Kanaele sind per Default aus und werden unabhaengig ueber
 * delivery.mail.enabled / delivery.discord.enabled eingeschaltet. Die Ziele kommen aus
 * delivery.&lt;kanal&gt;.destinations.&lt;name&gt; (z. B. per Env DELIVERY_DISCORD_DESTINATIONS_REPORTS).
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
    public MailChannelSender mailChannelSender(
            JavaMailSender mailSender,
            Environment environment,
            @Value("${delivery.mail.enabled:false}") boolean enabled,
            @Value("${delivery.mail.from:}") String from,
            @Value("${spring.mail.username:}") String smtpUsername,
            @Value("${delivery.mail.from-name:Cycling Report}") String fromName,
            @Value("${delivery.mail.subject-prefix:[Cycling Report]}") String subjectPrefix) {
        Map<String, String> destinations = destinations(environment, "delivery.mail.destinations");
        return new MailChannelSender(new MailSettings(enabled, from, smtpUsername, fromName, destinations, subjectPrefix),
                mailSender);
    }

    @Bean
    public DiscordChannelSender discordChannelSender(
            Sleeper sleeper,
            Environment environment,
            @Value("${delivery.discord.enabled:false}") boolean enabled,
            @Value("${delivery.discord.bot-token:}") String botToken,
            @Value("${delivery.discord.api-base-url:" + DiscordSettings.DEFAULT_API_BASE_URL + "}") String apiBaseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(30));
        RestClient restClient = RestClient.builder().requestFactory(requestFactory).build();
        Map<String, String> destinations = destinations(environment, "delivery.discord.destinations");
        return new DiscordChannelSender(new DiscordSettings(enabled, botToken, destinations, apiBaseUrl), restClient, sleeper);
    }

    @Bean
    public DeliveryService deliveryService(List<ChannelSender> senders,
                                           DeliveryRepository deliveries,
                                           MessageRepository messages,
                                           PlatformTransactionManager transactionManager,
                                           Clock clock,
                                           Environment environment) {
        Binder binder = Binder.get(environment);
        Duration payloadRetention = binder.bind("delivery.retention.payload", Duration.class).orElse(Duration.ofDays(7));
        Duration statusRetention = binder.bind("delivery.retention.status", Duration.class).orElse(Duration.ofDays(90));
        return new DeliveryService(senders, deliveries, messages, new MessageCodec(),
                new TransactionTemplate(transactionManager), clock,
                new DeliveryService.Retention(payloadRetention, statusRetention));
    }

    /** Eigener Single-Thread-Executor: Versand, Retry und Aufraeumen laufen nie parallel. */
    @Bean
    public DeliveryDispatcher deliveryDispatcher(DeliveryService deliveryService) {
        return new DeliveryDispatcher(deliveryService,
                Executors.newSingleThreadExecutor(Thread.ofPlatform().name("delivery").daemon().factory()));
    }

    /** Liest eine Map wie delivery.discord.destinations.* (auch aus Env-Variablen, relaxed binding). */
    private static Map<String, String> destinations(Environment environment, String prefix) {
        return Binder.get(environment).bind(prefix, Bindable.mapOf(String.class, String.class)).orElse(Map.of());
    }
}
