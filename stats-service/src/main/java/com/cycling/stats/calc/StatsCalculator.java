package com.cycling.stats.calc;

import com.cycling.stats.api.StatsDto;
import com.cycling.stats.api.StatsPeriodDto;
import com.cycling.stats.api.ZoneTimeDto;
import com.cycling.stats.store.RideRecord;
import com.cycling.stats.store.ZoneTime;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bildet aus gespeicherten Fahrten die Summen je Zeitraum. Ohne Zustand. */
public final class StatsCalculator {

    private static final Pattern ZONE_NUMBER = Pattern.compile("(\\d+)");

    private StatsCalculator() {
    }

    /**
     * @param rides Fahrten; nur die von {@code from} bis {@code to} (inklusive) zaehlen
     */
    public static StatsDto calculate(List<RideRecord> rides, LocalDate from, LocalDate to, Grouping groupBy) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from (" + from + ") liegt nach to (" + to + ")");
        }
        List<RideRecord> inRange = rides.stream()
                .filter(r -> !r.getDate().isBefore(from) && !r.getDate().isAfter(to))
                .sorted(Comparator.comparing(RideRecord::getDate))
                .toList();

        List<StatsPeriodDto> periods = new ArrayList<>();
        if (groupBy != Grouping.NONE) {
            for (LocalDate start = groupBy.periodStart(from); !start.isAfter(to);
                 start = groupBy.periodEnd(start).plusDays(1)) {
                LocalDate periodFrom = start.isBefore(from) ? from : start;
                LocalDate end = groupBy.periodEnd(start);
                LocalDate periodTo = end.isAfter(to) ? to : end;
                periods.add(period(inRange.stream()
                        .filter(r -> !r.getDate().isBefore(periodFrom) && !r.getDate().isAfter(periodTo))
                        .toList(), periodFrom, periodTo));
            }
        }
        return new StatsDto(from, to, groupBy, period(inRange, from, to), periods);
    }

    /** Summen ueber die Fahrten (aufsteigend nach Datum sortiert). */
    static StatsPeriodDto period(List<RideRecord> rides, LocalDate from, LocalDate to) {
        double distance = 0;
        long moving = 0;
        long elapsed = 0;
        double elevation = 0;
        double load = 0;
        int withPower = 0;
        int withHr = 0;
        for (RideRecord r : rides) {
            distance += r.getDistance();
            moving += r.getMovingTime();
            elapsed += r.getElapsedTime();
            elevation += r.getElevationGain();
            load += r.getTrainingLoad();
            if (hasTime(r.getPowerZones())) withPower++;
            if (hasTime(r.getHrZones())) withHr++;
        }
        return new StatsPeriodDto(from, to, rides.size(), round(distance, 0), moving, elapsed, round(elevation, 0),
                round(load, 1), withPower, withHr,
                zones(rides, RideRecord::getPowerZones), zones(rides, RideRecord::getHrZones));
    }

    /**
     * Summiert die Zeit je Zone. Name und Farbe kommen von der juengsten Fahrt, in der die Zone vorkommt.
     * Sortiert nach der Zonen-Nummer im Namen (Z1, Z2, ... Z10).
     */
    private static List<ZoneTimeDto> zones(List<RideRecord> rides, Function<RideRecord, List<ZoneTime>> zonesOf) {
        Map<String, long[]> seconds = new LinkedHashMap<>();
        Map<String, ZoneTime> latest = new LinkedHashMap<>();
        for (RideRecord ride : rides) {
            for (ZoneTime zone : zonesOf.apply(ride)) {
                seconds.computeIfAbsent(zone.zoneName(), _ -> new long[1])[0] += zone.seconds();
                latest.put(zone.zoneName(), zone);
            }
        }
        long total = seconds.values().stream().mapToLong(s -> s[0]).sum();
        if (total == 0) {
            return List.of();
        }
        return seconds.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, long[]> e) -> zoneNumber(e.getKey()))
                        .thenComparing(Map.Entry::getKey))
                .map(e -> {
                    ZoneTime info = latest.get(e.getKey());
                    long s = e.getValue()[0];
                    return new ZoneTimeDto(e.getKey(), info.zoneLabel() != null ? info.zoneLabel() : "", s,
                            round(s * 100.0 / total, 1), info.color());
                })
                .toList();
    }

    private static boolean hasTime(List<ZoneTime> zones) {
        return zones.stream().anyMatch(z -> z.seconds() > 0);
    }

    private static int zoneNumber(String name) {
        Matcher m = ZONE_NUMBER.matcher(name);
        return m.find() ? Integer.parseInt(m.group(1)) : Integer.MAX_VALUE;
    }

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}
