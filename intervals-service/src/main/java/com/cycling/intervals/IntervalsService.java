package com.cycling.intervals;

import com.cycling.intervals.api.IntervalsActivity;
import com.cycling.intervals.api.IntervalsActivityZones;
import com.cycling.intervals.api.IntervalsAthleteThresholds;
import com.cycling.intervals.api.IntervalsAthleteZones;
import com.cycling.intervals.api.IntervalsPowerCurve;
import com.cycling.intervals.api.IntervalsStreams;
import com.cycling.intervals.api.IntervalsThresholdsUpdate;
import com.cycling.intervals.api.IntervalsWellnessPoint;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * Daten von intervals.icu mit Caffeine-Caches, damit wiederholte Anfragen (Report-Abruf, erneuter Versand,
 * Neuladen im Frontend) intervals.icu nicht erneut belasten. Fehler werden nicht gecacht.
 *
 * <ul>
 *   <li>Aktivitaetsliste: 2 min</li>
 *   <li>Wellness: 15 min</li>
 *   <li>Athleten-Zonen und Schwellenwerte: 1 h</li>
 *   <li>Streams, Zonenzeiten, Power-Curve einer Aktivitaet: 6 h, je hoechstens 50 Aktivitaeten</li>
 * </ul>
 */
public class IntervalsService {

    private static final Logger log = LoggerFactory.getLogger(IntervalsService.class);
    private static final String ATHLETE_KEY = "athlete";

    private final IntervalsGateway gateway;
    private final Cache<String, List<IntervalsActivity>> activities = cache(Duration.ofMinutes(2), 32);
    private final Cache<String, List<IntervalsWellnessPoint>> wellness = cache(Duration.ofMinutes(15), 64);
    private final Cache<String, IntervalsAthleteZones> athleteZones = cache(Duration.ofHours(1), 1);
    private final Cache<String, IntervalsStreams> streams = cache(Duration.ofHours(6), 50);
    private final Cache<String, IntervalsActivityZones> activityZones = cache(Duration.ofHours(6), 50);
    private final Cache<String, IntervalsPowerCurve> powerCurves = cache(Duration.ofHours(6), 50);

    public IntervalsService(IntervalsGateway gateway) {
        this.gateway = gateway;
    }

    public List<IntervalsActivity> activities(LocalDate from, LocalDate to) {
        return activities.get(from + "_" + to, k -> gateway.activities(from, to));
    }

    public List<IntervalsWellnessPoint> wellness(LocalDate from, LocalDate to) {
        return wellness.get(from + "_" + to, k -> gateway.wellness(from, to));
    }

    public IntervalsAthleteZones athleteZones() {
        return athleteZones.get(ATHLETE_KEY, k -> gateway.athleteZones());
    }

    public IntervalsAthleteThresholds thresholds() {
        return athleteZones().thresholds();
    }

    public IntervalsStreams streams(String activityId) {
        return streams.get(activityId, gateway::streams);
    }

    public IntervalsActivityZones activityZones(String activityId) {
        return activityZones.get(activityId, id -> gateway.activityZones(id, athleteZones()));
    }

    public IntervalsPowerCurve powerCurve(String activityId) {
        return powerCurves.get(activityId, gateway::powerCurve);
    }

    /**
     * Schreibt neue Schwellenwerte nach intervals.icu. Danach sind alle Caches leer (Zonen, Zonenzeiten
     * und Power-Curves haengen an FTP/LTHR) und die frischen Athleten-Zonen liegen im Cache.
     */
    public IntervalsAthleteZones updateThresholds(IntervalsThresholdsUpdate update) {
        try {
            IntervalsAthleteZones fresh = gateway.updateThresholds(update);
            evictAll();
            athleteZones.put(ATHLETE_KEY, fresh);
            return fresh;
        } catch (RuntimeException e) {
            // auch ein teilweise geglueckter Schreibvorgang darf keinen alten Stand im Cache lassen
            evictAll();
            throw e;
        }
    }

    /** Leert alle Caches, z. B. nach einer FTP-Aenderung in intervals.icu. */
    public void evictAll() {
        List.of(activities, wellness, athleteZones, streams, activityZones, powerCurves)
                .forEach(Cache::invalidateAll);
        log.info("Alle intervals.icu-Caches geleert");
    }

    private static <V> Cache<String, V> cache(Duration ttl, long maxSize) {
        return Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(maxSize).build();
    }
}
