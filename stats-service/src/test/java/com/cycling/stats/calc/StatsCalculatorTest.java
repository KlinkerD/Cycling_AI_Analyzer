package com.cycling.stats.calc;

import com.cycling.stats.api.StatsDto;
import com.cycling.stats.api.StatsPeriodDto;
import com.cycling.stats.api.ZoneTimeDto;
import com.cycling.stats.store.RideRecord;
import com.cycling.stats.store.ZoneTime;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatsCalculatorTest {

    private static final LocalDate JAN_1 = LocalDate.of(2026, 1, 1);

    @Test
    void sumsTotalsAndZonesOverRange() {
        List<RideRecord> rides = List.of(
                ride("a", JAN_1, 40_000, 3600, 300, 50,
                        List.of(zone("Z1", 600), zone("Z2", 3000)), List.of(zone("Z1", 1800))),
                ride("b", LocalDate.of(2026, 1, 3), 20_000, 1800, 100, 25.5,
                        List.of(zone("Z2", 1000), zone("Z4", 400)), List.of()));

        StatsPeriodDto total = StatsCalculator.calculate(rides, JAN_1, LocalDate.of(2026, 1, 31), Grouping.NONE).total();

        assertThat(total.rides()).isEqualTo(2);
        assertThat(total.distance()).isEqualTo(60_000);
        assertThat(total.movingTime()).isEqualTo(5400);
        assertThat(total.elevationGain()).isEqualTo(400);
        assertThat(total.trainingLoad()).isEqualTo(75.5);
        assertThat(total.ridesWithPower()).isEqualTo(2);
        assertThat(total.ridesWithHeartRate()).isEqualTo(1);
        assertThat(total.powerZones()).extracting(ZoneTimeDto::zone, ZoneTimeDto::seconds)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Z1", 600L),
                        org.assertj.core.groups.Tuple.tuple("Z2", 4000L),
                        org.assertj.core.groups.Tuple.tuple("Z4", 400L));
        assertThat(total.powerZones().get(1).percentage()).isEqualTo(80.0);
        assertThat(total.hrZones()).extracting(ZoneTimeDto::zone).containsExactly("Z1");
    }

    @Test
    void ridesWithoutZonesCountForDistanceButNotForZones() {
        StatsPeriodDto total = StatsCalculator.calculate(
                List.of(ride("a", JAN_1, 10_000, 1200, 0, 0, List.of(), List.of())),
                JAN_1, JAN_1, Grouping.NONE).total();

        assertThat(total.rides()).isEqualTo(1);
        assertThat(total.distance()).isEqualTo(10_000);
        assertThat(total.ridesWithPower()).isZero();
        assertThat(total.powerZones()).isEmpty();
    }

    @Test
    void ignoresRidesOutsideRange() {
        StatsDto stats = StatsCalculator.calculate(
                List.of(ride("old", LocalDate.of(2025, 12, 31), 50_000, 3600, 0, 0, List.of(), List.of())),
                JAN_1, LocalDate.of(2026, 1, 10), Grouping.NONE);

        assertThat(stats.total().rides()).isZero();
        assertThat(stats.periods()).isEmpty();
    }

    @Test
    void firstWeekOfYearStartsOnJanuaryFirst() {
        // 1.1.2026 ist ein Donnerstag: die erste Woche ist auf 1.1. bis 4.1. zugeschnitten.
        StatsDto stats = StatsCalculator.calculate(
                List.of(ride("a", JAN_1, 1000, 60, 0, 0, List.of(), List.of()),
                        ride("b", LocalDate.of(2026, 1, 5), 2000, 60, 0, 0, List.of(), List.of())),
                JAN_1, LocalDate.of(2026, 1, 14), Grouping.WEEK);

        assertThat(stats.periods()).extracting(StatsPeriodDto::from, StatsPeriodDto::to, StatsPeriodDto::rides)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(JAN_1, LocalDate.of(2026, 1, 4), 1),
                        org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 11), 1),
                        org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 1, 12), LocalDate.of(2026, 1, 14), 0));
    }

    @Test
    void monthsIncludeEmptyMonthsAndEndAtToday() {
        StatsDto stats = StatsCalculator.calculate(
                List.of(ride("a", LocalDate.of(2026, 3, 15), 1000, 60, 0, 0, List.of(), List.of())),
                JAN_1, LocalDate.of(2026, 3, 20), Grouping.MONTH);

        assertThat(stats.periods()).extracting(StatsPeriodDto::from, StatsPeriodDto::to, StatsPeriodDto::rides)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(JAN_1, LocalDate.of(2026, 1, 31), 0),
                        org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28), 0),
                        org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 20), 1));
    }

    @Test
    void yearsFollowCalendar() {
        StatsDto stats = StatsCalculator.calculate(List.of(), LocalDate.of(2025, 6, 1), LocalDate.of(2026, 2, 1),
                Grouping.YEAR);

        assertThat(stats.periods()).extracting(StatsPeriodDto::from, StatsPeriodDto::to)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(LocalDate.of(2025, 6, 1), LocalDate.of(2025, 12, 31)),
                        org.assertj.core.groups.Tuple.tuple(JAN_1, LocalDate.of(2026, 2, 1)));
    }

    @Test
    void sortsZonesByNumberAndTakesLabelFromNewestRide() {
        StatsPeriodDto total = StatsCalculator.calculate(List.of(
                        ride("a", JAN_1, 0, 0, 0, 0,
                                List.of(new ZoneTime("Z10", "", 10, null), new ZoneTime("Z2", "Alt", 10, "#111")),
                                List.of()),
                        ride("b", LocalDate.of(2026, 1, 2), 0, 0, 0, 0,
                                List.of(new ZoneTime("Z2", "Endurance", 10, "#222")), List.of())),
                JAN_1, LocalDate.of(2026, 1, 2), Grouping.NONE).total();

        assertThat(total.powerZones()).extracting(ZoneTimeDto::zone).containsExactly("Z2", "Z10");
        assertThat(total.powerZones().getFirst().label()).isEqualTo("Endurance");
        assertThat(total.powerZones().getFirst().color()).isEqualTo("#222");
        assertThat(total.powerZones().get(1).label()).isEmpty();
    }

    @Test
    void rejectsFromAfterTo() {
        assertThatThrownBy(() -> StatsCalculator.calculate(List.of(), LocalDate.of(2026, 2, 1), JAN_1, Grouping.MONTH))
                .isInstanceOf(IllegalArgumentException.class);
    }

    static RideRecord ride(String id, LocalDate date, double distance, int movingTime, double elevation, double load,
                           List<ZoneTime> power, List<ZoneTime> hr) {
        RideRecord r = new RideRecord(id);
        r.setDate(date);
        r.setDistance(distance);
        r.setMovingTime(movingTime);
        r.setElapsedTime(movingTime);
        r.setElevationGain(elevation);
        r.setTrainingLoad(load);
        r.setPowerZones(power);
        r.setHrZones(hr);
        r.setZonesLoaded(true);
        return r;
    }

    private static ZoneTime zone(String name, int seconds) {
        return new ZoneTime(name, "", seconds, null);
    }
}
