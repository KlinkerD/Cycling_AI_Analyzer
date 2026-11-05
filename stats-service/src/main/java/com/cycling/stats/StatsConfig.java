package com.cycling.stats;

import com.cycling.intervals.client.IntervalsClient;
import com.cycling.messaging.MqttMessaging;
import com.cycling.messaging.MqttSettings;
import com.cycling.messaging.Topics;
import com.cycling.messaging.contract.ActivityDetected;
import com.cycling.stats.store.RideRepository;
import com.cycling.stats.sync.ReconcileSchedule;
import com.cycling.stats.sync.RideSource;
import com.cycling.stats.sync.RideSync;
import com.cycling.web.CyclingWebConfig;
import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Verdrahtet den stats-service.
 * <ul>
 *   <li>REST zum intervals-service (Fahrten, Zonenzeiten), REST-API fuer das Frontend (/api/stats)</li>
 *   <li>MQTT rein: ActivityDetected (neue Fahrt speichern)</li>
 *   <li>Eigene H2-Datenbank mit einer Zeile pro Fahrt</li>
 * </ul>
 */
@Configuration
@Import(CyclingWebConfig.class)
public class StatsConfig {

    @Bean
    public OpenAPI statsOpenApi() {
        return CyclingWebConfig.openApi("Cycling Stats API");
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

    /** Zeit und Zeitzone fuer "heute" und den 1.1. */
    @Bean
    public Clock statsClock(@Value("${stats.zone:Europe/Berlin}") String zone) {
        try {
            return Clock.system(ZoneId.of(zone.trim()));
        } catch (RuntimeException e) {
            throw new IllegalStateException("stats.zone: '" + zone + "' ist keine Zeitzone (z. B. Europe/Berlin)", e);
        }
    }

    @Bean
    public IntervalsClient intervalsClient(@Value("${intervals-service.base-url}") String baseUrl,
                                           @Value("${intervals-service.connect-timeout:5s}") Duration connectTimeout,
                                           @Value("${intervals-service.read-timeout:60s}") Duration readTimeout) {
        return IntervalsClient.create(baseUrl, connectTimeout, readTimeout);
    }

    /** Ein Thread fuer alle Schreibvorgaenge: Erstimport, Meldungen und Abgleich laufen nacheinander. */
    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService statsSyncWorker() {
        return Executors.newSingleThreadExecutor(Thread.ofPlatform().name("stats-sync").daemon(true).factory());
    }

    @Bean
    public RideSync rideSync(IntervalsClient intervals, RideRepository repository, Clock clock,
                             ExecutorService statsSyncWorker,
                             @Value("${stats.import.years-back:1}") int importYearsBack,
                             @Value("${stats.reconcile.days:30}") int reconcileDays) {
        return new RideSync(RideSource.of(intervals), repository, clock, statsSyncWorker, importYearsBack,
                reconcileDays);
    }

    @Bean
    public ReconcileSchedule reconcileSchedule(RideSync sync) {
        return new ReconcileSchedule(sync);
    }

    /**
     * Erstimport anstossen, Handler registrieren und erst danach verbinden. Meldungen, die waehrend des
     * Imports kommen, warten auf dem Worker, bis er fertig ist.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void start(ApplicationReadyEvent event) {
        var context = event.getApplicationContext();
        RideSync sync = context.getBean(RideSync.class);
        MqttMessaging messaging = context.getBean(MqttMessaging.class);

        if (context.getEnvironment().getProperty("stats.import.on-start", Boolean.class, true)) {
            sync.requestImport();
        }
        messaging.subscribe(Topics.INTERVALS_ACTIVITY_DETECTED, ActivityDetected.class, sync::onActivityDetected);
        messaging.start();
    }
}
