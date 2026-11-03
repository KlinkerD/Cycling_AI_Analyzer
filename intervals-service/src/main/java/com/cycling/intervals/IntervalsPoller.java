package com.cycling.intervals;

import com.cycling.intervals.api.IntervalsActivity;
import com.cycling.intervals.api.IntervalsWellnessPoint;
import com.cycling.messaging.MessagePublisher;
import com.cycling.messaging.Topics;
import com.cycling.messaging.contract.ActivityDetected;
import com.cycling.messaging.contract.WellnessUpdated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Fragt intervals.icu regelmaessig ab (am Cache vorbei) und meldet per MQTT:
 * <ul>
 *   <li>bei jedem Lauf alle Rad-Aktivitaeten der letzten Tage ({@link ActivityDetected}). Der
 *       analysis-service dedupliziert (Report vorhanden, Analyse laeuft) und startet so auch
 *       fehlgeschlagene oder abgelaufene Analysen beim naechsten Lauf erneut.</li>
 *   <li>die heutigen Fitness-Werte, wenn sie sich (auf eine Nachkommastelle gerundet) geaendert haben
 *       ({@link WellnessUpdated}, retained).</li>
 * </ul>
 * Schlaegt das Senden fehl, wird beim naechsten Lauf erneut gemeldet.
 */
public class IntervalsPoller {

    private static final Logger log = LoggerFactory.getLogger(IntervalsPoller.class);

    private final IntervalsGateway gateway;
    private final MessagePublisher publisher;
    private final Clock clock;
    private final int windowDays;

    /** Zuletzt gemeldete Werte von heute. Nur vom Scheduler-Thread genutzt. */
    private WellnessUpdated.Values lastWellness;

    public IntervalsPoller(IntervalsGateway gateway, MessagePublisher publisher, Clock clock, int windowDays) {
        this.gateway = gateway;
        this.publisher = publisher;
        this.clock = clock;
        this.windowDays = windowDays;
    }

    @Scheduled(fixedDelayString = "${intervals.poll.interval:15m}",
            initialDelayString = "${intervals.poll.initial-delay:30s}")
    public void poll() {
        pollActivities();
        pollWellness();
    }

    void pollActivities() {
        LocalDate today = LocalDate.now(clock);
        List<IntervalsActivity> recent;
        try {
            recent = gateway.activities(today.minusDays(windowDays), today.plusDays(1));
        } catch (RuntimeException e) {
            log.warn("Neue Aktivitaeten konnten nicht abgefragt werden: {}", e.getMessage());
            return;
        }
        int sent = 0;
        for (IntervalsActivity activity : recent) {
            if (activity.id() == null) continue;
            try {
                publisher.publish(Topics.INTERVALS_ACTIVITY_DETECTED, toMessage(activity));
                sent++;
            } catch (RuntimeException e) {
                log.warn("Aktivitaet {} konnte nicht gemeldet werden, naechster Lauf versucht es erneut: {}",
                        activity.id(), e.getMessage());
            }
        }
        log.info("{} Rad-Aktivitaet(en) der letzten {} Tage gemeldet", sent, windowDays);
    }

    void pollWellness() {
        LocalDate today = LocalDate.now(clock);
        List<IntervalsWellnessPoint> points;
        try {
            points = gateway.wellness(today.minusDays(1), today);
        } catch (RuntimeException e) {
            log.warn("Fitness-Werte konnten nicht abgefragt werden: {}", e.getMessage());
            return;
        }
        WellnessUpdated.Values current = find(points, today);
        if (current == null || current.equals(lastWellness)) return;

        WellnessUpdated message = new WellnessUpdated(current, find(points, today.minusDays(1)));
        try {
            publisher.publishRetained(Topics.INTERVALS_WELLNESS_UPDATED, message);
            lastWellness = current;
            log.info("Fitness-Werte gemeldet: CTL={} ATL={} Form={} Ramp={}",
                    current.ctl(), current.atl(), current.form(), current.rampRate());
        } catch (RuntimeException e) {
            log.warn("Fitness-Werte konnten nicht gemeldet werden: {}", e.getMessage());
        }
    }

    private static WellnessUpdated.Values find(List<IntervalsWellnessPoint> points, LocalDate date) {
        String key = date.toString();
        return points.stream()
                .filter(p -> key.equals(p.date()))
                .findFirst()
                .map(p -> new WellnessUpdated.Values(p.date(), round(p.ctl()), round(p.atl()), round(p.form()),
                        round(p.rampRate())))
                .orElse(null);
    }

    private static Double round(Double value) {
        return value == null ? null : Math.round(value * 10.0) / 10.0;
    }

    private static ActivityDetected toMessage(IntervalsActivity a) {
        return new ActivityDetected(a.id(), a.name(), a.startDateLocal(), a.type(), a.distance(), a.movingTime(),
                a.elapsedTime(), a.averageWatts(), a.normalizedPower(), a.maxWatts(), a.averageHeartrate(),
                a.maxHeartrate(), a.averageSpeed(), a.totalElevationGain());
    }
}
