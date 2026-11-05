package com.cycling.stats.api;

import com.cycling.stats.calc.Grouping;
import com.cycling.stats.store.RideRecord;
import com.cycling.stats.store.RideRepository;
import com.cycling.stats.store.ZoneTime;
import com.cycling.stats.sync.RideSync;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Startet den ganzen Service (ohne Broker und intervals-service) und fragt die REST-API ab. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "mqtt.broker-url=tcp://localhost:1",
        "mqtt.client-id=stats-controller-test",
        "intervals-service.base-url=http://localhost:1",
        "stats.import.on-start=false",
        "spring.datasource.url=jdbc:h2:mem:stats-controller;DB_CLOSE_DELAY=-1"
})
class StatsControllerTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private RideRepository repository;

    @Autowired
    private RideSync sync;

    @Autowired
    private Clock clock;

    private RestClient http;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        http = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void statsForRangeGroupedByMonth() {
        repository.save(ride("a", LocalDate.of(2026, 1, 10), 40_000));
        repository.save(ride("b", LocalDate.of(2026, 2, 10), 60_000));

        StatsDto stats = http.get().uri("/api/stats?from=2026-01-01&to=2026-02-28&groupBy=MONTH")
                .retrieve().body(StatsDto.class);

        assertThat(stats.groupBy()).isEqualTo(Grouping.MONTH);
        assertThat(stats.total().distance()).isEqualTo(100_000);
        assertThat(stats.periods()).extracting(StatsPeriodDto::distance).containsExactly(40_000.0, 60_000.0);
        assertThat(stats.total().powerZones()).extracting(ZoneTimeDto::seconds).containsExactly(1200L);
    }

    @Test
    void yearToDateStartsOnJanuaryFirstAndComparesWithPreviousYear() {
        LocalDate today = LocalDate.now(clock);
        repository.save(ride("now", today.withDayOfYear(1), 10_000));
        repository.save(ride("lastYear", today.minusYears(1).withDayOfYear(1), 20_000));
        repository.save(ride("lastYearLater", today.minusYears(1).plusDays(1), 99_000));

        YearToDateDto ytd = http.get().uri("/api/stats/year-to-date?groupBy=NONE").retrieve().body(YearToDateDto.class);

        assertThat(ytd.year()).isEqualTo(today.getYear());
        assertThat(ytd.current().from()).isEqualTo(today.withDayOfYear(1));
        assertThat(ytd.current().to()).isEqualTo(today);
        assertThat(ytd.current().total().distance()).isEqualTo(10_000);
        assertThat(ytd.previousYear().to()).isEqualTo(today.minusYears(1));
        assertThat(ytd.previousYear().total().distance()).isEqualTo(20_000);
    }

    @Test
    void statusCountsRides() {
        repository.save(ride("a", LocalDate.of(2026, 1, 10), 1000));

        SyncStatusDto status = http.get().uri("/api/stats/status").retrieve().body(SyncStatusDto.class);

        assertThat(status.rides()).isEqualTo(1);
        assertThat(status.oldestRide()).isEqualTo(LocalDate.of(2026, 1, 10));
        assertThat(status.importStart()).isEqualTo(sync.importStart());
    }

    @Test
    void rejectsFromAfterToAndUnknownGrouping() {
        HttpStatusCode reversed = http.get().uri("/api/stats?from=2026-02-01&to=2026-01-01")
                .exchange((req, res) -> res.getStatusCode());
        HttpStatusCode unknown = http.get().uri("/api/stats?from=2026-01-01&to=2026-02-01&groupBy=DAY")
                .exchange((req, res) -> res.getStatusCode());

        assertThat(reversed.value()).isEqualTo(400);
        assertThat(unknown.value()).isEqualTo(400);
    }

    private static RideRecord ride(String id, LocalDate date, double distance) {
        RideRecord r = new RideRecord(id);
        r.setDate(date);
        r.setDistance(distance);
        r.setMovingTime(3600);
        r.setPowerZones(List.of(new ZoneTime("Z2", "Endurance", 600, "#bbb")));
        r.setZonesLoaded(true);
        return r;
    }
}
