package com.cycling.report.analysis;

import com.cycling.types.ActivityId;

import com.cycling.intervals.api.IntervalsActivityZones;
import com.cycling.intervals.api.IntervalsAthleteThresholds;
import com.cycling.intervals.api.IntervalsAthleteZones;
import com.cycling.intervals.api.IntervalsPowerCurve;
import com.cycling.intervals.api.IntervalsStreams;
import com.cycling.intervals.api.IntervalsZone;
import com.cycling.intervals.api.IntervalsZoneShare;
import com.cycling.intervals.client.IntervalsClient;
import com.cycling.intervals.client.IntervalsUnavailableException;
import com.cycling.report.api.ActivityDto;
import com.cycling.report.api.StreamDataDto;
import com.cycling.report.api.ZoneBandDto;
import com.cycling.report.api.ZoneDistributionDto;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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

    /**
     * Eine Aktivitaet aus der Liste rund um ihr Datum (+/- 1 Tag, wegen Zeitzonen); leer, wenn sie dort nicht
     * auftaucht oder der Abruf scheitert.
     */
    public Optional<ActivityDto> findActivity(ActivityId activityId, LocalDate around) {
        if (around == null) return Optional.empty();
        try {
            return client.activities(around.minusDays(1), around.plusDays(1)).stream()
                    .filter(a -> activityId.equals(a.id()))
                    .findFirst()
                    .map(ActivityDto::from);
        } catch (IntervalsUnavailableException e) {
            return Optional.empty();
        }
    }

    public StreamDataDto getStreams(ActivityId activityId) {
        IntervalsStreams s = client.streams(activityId);
        return new StreamDataDto(list(s.distance()), list(s.watts()), list(s.heartrate()), list(s.velocity()),
                list(s.altitude()), list(s.time()), list(s.cadence()), list(s.lat()), list(s.lng()));
    }

    /**
     * Zonengrenzen fuer die Karte; fail-soft leer. Leistungszonen Z1-Z7 (ohne Sweet Spot) aus den Prozentgrenzen
     * mal {@code ftp}, damit sie zur FTP der Fahrt passen; ohne FTP die aktuellen Wattgrenzen. HF-Zonen mit den
     * aktuellen bpm-Grenzen.
     */
    public ZoneBands getZoneBands(Integer ftp) {
        return toBands(client.athleteZonesOrEmpty(), ftp);
    }

    /** Wie {@link #getZoneBands}, wirft aber {@link IntervalsUnavailableException}, statt leer zu liefern. */
    public ZoneBands getZoneBandsOrThrow(Integer ftp) {
        return toBands(client.athleteZones(), ftp);
    }

    private static ZoneBands toBands(IntervalsAthleteZones zones, Integer ftp) {
        List<ZoneBandDto> power = (zones.powerZones() != null ? zones.powerZones() : List.<IntervalsZone>of()).stream()
                .filter(z -> z.index() >= 1 && z.index() <= 7)
                .map(z -> powerBand(z, ftp))
                .filter(Objects::nonNull)
                .toList();
        List<ZoneBandDto> hr = (zones.hrZones() != null ? zones.hrZones() : List.<IntervalsZone>of()).stream()
                .filter(z -> z.lowerValue() != null)
                .map(z -> new ZoneBandDto(z.name(), z.lowerValue(), z.upperValue(), z.color()))
                .toList();
        return new ZoneBands(power, hr);
    }

    /** Leistungs- und HF-Zonengrenzen fuer die Karte. */
    public record ZoneBands(List<ZoneBandDto> power, List<ZoneBandDto> hr) {
        public static ZoneBands empty() {
            return new ZoneBands(List.of(), List.of());
        }
    }

    private static ZoneBandDto powerBand(IntervalsZone z, Integer ftp) {
        if (ftp != null && ftp > 0 && z.lowerPct() != null) {
            Integer upper = z.upperPct() == null ? null : (int) Math.round(z.upperPct() * ftp);
            return new ZoneBandDto(z.name(), (int) Math.round(z.lowerPct() * ftp), upper, z.color());
        }
        if (z.lowerValue() == null) return null;
        return new ZoneBandDto(z.name(), z.lowerValue(), z.upperValue(), z.color());
    }

    /** Fail-soft: leere Schwellenwerte, wenn der Abruf scheitert. */
    public AthleteThresholdsDto getAthleteThresholds() {
        IntervalsAthleteThresholds t = client.athleteThresholdsOrEmpty();
        return new AthleteThresholdsDto(t.name(), t.ftp(), t.indoorFtp(), t.lthr(), t.maxHr(), t.weight());
    }

    /** Fail-soft: leere Zonen und unbekannte Indoor-Kennung/FTP, wenn der Abruf scheitert. */
    public ActivityZonesDto getActivityZones(ActivityId activityId) {
        return client.activityZonesIfAvailable(activityId)
                .map(IntervalsData::toZones)
                .orElseGet(() -> new ActivityZonesDto(List.of(), List.of(), null, null));
    }

    /** Wie {@link #getActivityZones}, wirft aber {@link IntervalsUnavailableException}, statt leer zu liefern. */
    public ActivityZonesDto getActivityZonesOrThrow(ActivityId activityId) {
        return toZones(client.activityZones(activityId));
    }

    /** Power-Curve als "Dauer in s -> Watt"; fail-soft leer. */
    public SortedMap<Integer, Integer> getActivityPowerCurve(ActivityId activityId) {
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
