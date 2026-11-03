package com.cycling.report.analysis;

import com.cycling.intervals.api.IntervalsActivityZones;
import com.cycling.intervals.api.IntervalsAthleteThresholds;
import com.cycling.intervals.api.IntervalsPowerCurve;
import com.cycling.intervals.api.IntervalsStreams;
import com.cycling.intervals.api.IntervalsZoneShare;
import com.cycling.intervals.client.IntervalsClient;
import com.cycling.intervals.client.IntervalsUnavailableException;
import com.cycling.report.api.ActivityDto;
import com.cycling.report.api.StreamDataDto;
import com.cycling.report.api.ZoneDistributionDto;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Die intervals.icu-Daten, die Analyse und Report brauchen, umgewandelt in die eigenen DTOs. Holt sie ueber
 * den gemeinsamen {@link IntervalsClient}.
 *
 * <p>Fail-soft wie bisher: Schwellenwerte, Zonen und Power-Curve liefern bei Fehlern leere Werte (Report und
 * Analyse kommen ohne aus). Aktivitaeten und Streams werfen {@link IntervalsUnavailableException}.
 */
public class IntervalsData {

    private final IntervalsClient client;

    public IntervalsData(IntervalsClient client) {
        this.client = client;
    }

    /** Rad-Aktivitaeten im Zeitraum (yyyy-MM-dd). */
    public List<ActivityDto> getActivities(String from, String to) {
        return client.activities(LocalDate.parse(from), LocalDate.parse(to)).stream().map(ActivityDto::from).toList();
    }

    public StreamDataDto getStreams(String activityId) {
        IntervalsStreams s = client.streams(activityId);
        return new StreamDataDto(list(s.distance()), list(s.watts()), list(s.heartrate()), list(s.velocity()),
                list(s.altitude()), list(s.time()), list(s.cadence()));
    }

    /** Fail-soft: leere Schwellenwerte, wenn der Abruf scheitert. */
    public AthleteThresholdsDto getAthleteThresholds() {
        IntervalsAthleteThresholds t = client.athleteThresholdsOrEmpty();
        return new AthleteThresholdsDto(t.name(), t.ftp(), t.indoorFtp(), t.lthr(), t.maxHr(), t.weight());
    }

    /** Fail-soft: leere Zonen und unbekannte Indoor-Kennung/FTP, wenn der Abruf scheitert. */
    public ActivityZonesDto getActivityZones(String activityId) {
        return client.activityZonesIfAvailable(activityId)
                .map(IntervalsData::toZones)
                .orElseGet(() -> new ActivityZonesDto(List.of(), List.of(), null, null));
    }

    /** Power-Curve als "Dauer in s -> Watt"; fail-soft leer. */
    public SortedMap<Integer, Integer> getActivityPowerCurve(String activityId) {
        TreeMap<Integer, Integer> out = new TreeMap<>();
        for (IntervalsPowerCurve.Point p : client.powerCurveOrEmpty(activityId).points()) {
            out.put(p.seconds(), p.watts());
        }
        return out;
    }

    private static ActivityZonesDto toZones(IntervalsActivityZones z) {
        return new ActivityZonesDto(shares(z.powerZones()), shares(z.hrZones()), z.indoor(), z.icuFtp());
    }

    private static List<ZoneDistributionDto> shares(List<IntervalsZoneShare> shares) {
        if (shares == null) return List.of();
        return shares.stream()
                .map(s -> new ZoneDistributionDto(s.zoneName(), s.zoneLabel(), s.seconds(), s.percentage(), s.color()))
                .toList();
    }

    private static <T> List<T> list(List<T> values) {
        return values != null ? values : Collections.emptyList();
    }
}
