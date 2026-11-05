package com.cycling.stats.sync;

import com.cycling.intervals.api.IntervalsActivity;
import com.cycling.intervals.api.IntervalsActivityZones;
import com.cycling.intervals.api.IntervalsZoneShare;
import com.cycling.intervals.client.IntervalsUnavailableException;
import com.cycling.messaging.contract.ActivityDetected;
import com.cycling.stats.store.RideRecord;
import com.cycling.stats.store.RideRepository;
import com.cycling.types.ActivityId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** RideSync gegen die echte (In-Memory-)Datenbank, mit einem Fake statt des intervals-service. */
@SpringBootTest(properties = {
        "mqtt.broker-url=tcp://localhost:1",
        "mqtt.client-id=stats-sync-test",
        "intervals-service.base-url=http://localhost:1",
        "stats.import.on-start=false",
        "spring.datasource.url=jdbc:h2:mem:stats-sync;DB_CLOSE_DELAY=-1"
})
class RideSyncTest {

    /** 5.11.2026, 12:00 in Berlin */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-05T11:00:00Z"), ZoneId.of("Europe/Berlin"));

    @Autowired
    private RideRepository repository;

    private FakeSource source;
    private RideSync sync;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        source = new FakeSource();
        // Direkt ausfuehren statt auf dem Worker-Thread
        sync = new RideSync(source, repository, CLOCK, Runnable::run, 1, 30);
    }

    @Test
    void importLoadsEverythingSinceJanuaryFirstOfPreviousYear() {
        source.add(activity("i1", LocalDate.of(2025, 1, 1), 50.0));
        source.add(activity("i2", LocalDate.of(2026, 11, 4), 80.0));

        sync.requestImport();

        assertThat(source.requestedRanges).containsExactly(LocalDate.of(2025, 1, 1) + ".." + LocalDate.of(2026, 11, 5));
        assertThat(repository.findAll()).hasSize(2);
        RideRecord ride = repository.findById("i2").orElseThrow();
        assertThat(ride.getTrainingLoad()).isEqualTo(80.0);
        assertThat(ride.isZonesLoaded()).isTrue();
        assertThat(ride.getPowerZones()).extracting(z -> z.zoneName()).containsExactly("Z1", "Z2");
        assertThat(sync.importRunning()).isFalse();
    }

    @Test
    void importSkipsRidesThatAlreadyHaveZones() {
        source.add(activity("i1", LocalDate.of(2026, 3, 1), 50.0));
        sync.requestImport();
        source.zoneCalls.clear();

        sync.requestImport();

        assertThat(source.zoneCalls).isEmpty();
    }

    @Test
    void detectedRideIsStoredWithTrainingLoadFromActivityList() {
        source.add(activity("i9", LocalDate.of(2026, 11, 5), 65.0));

        sync.onActivityDetected(detected("i9", LocalDate.of(2026, 11, 5)));

        RideRecord ride = repository.findById("i9").orElseThrow();
        assertThat(ride.getTrainingLoad()).isEqualTo(65.0);
        assertThat(ride.getHrZones()).extracting(z -> z.seconds()).containsExactly(1200);
    }

    @Test
    void repeatedDetectedMessageDoesNotReloadZones() {
        source.add(activity("i9", LocalDate.of(2026, 11, 5), 65.0));
        sync.onActivityDetected(detected("i9", LocalDate.of(2026, 11, 5)));
        source.zoneCalls.clear();

        sync.onActivityDetected(detected("i9", LocalDate.of(2026, 11, 5)));

        assertThat(source.zoneCalls).isEmpty();
    }

    @Test
    void detectedRideMissingFromListFallsBackToMessage() {
        sync.onActivityDetected(detected("i7", LocalDate.of(2026, 11, 5)));

        RideRecord ride = repository.findById("i7").orElseThrow();
        assertThat(ride.getDistance()).isEqualTo(30_000);
        assertThat(ride.getTrainingLoad()).isZero();
    }

    @Test
    void failedZonesAreRetriedByReconcile() {
        source.add(activity("i1", LocalDate.of(2025, 5, 1), 50.0));
        source.failingZones.add("i1");
        sync.requestImport();
        assertThat(repository.findById("i1").orElseThrow().isZonesLoaded()).isFalse();

        source.failingZones.clear();
        sync.requestReconcile();

        assertThat(repository.findById("i1").orElseThrow().isZonesLoaded()).isTrue();
    }

    @Test
    void reconcileRemovesRidesDeletedInIntervalsAndUpdatesChanged() {
        source.add(activity("keep", LocalDate.of(2026, 11, 1), 50.0));
        source.add(activity("gone", LocalDate.of(2026, 10, 20), 40.0));
        source.add(activity("old", LocalDate.of(2026, 1, 10), 30.0));
        sync.requestImport();

        source.remove("gone");
        source.remove("old"); // ausserhalb des 30-Tage-Fensters: bleibt gespeichert
        source.add(activity("keep", LocalDate.of(2026, 11, 1), 99.0));
        sync.requestReconcile();

        assertThat(repository.findById("gone")).isEmpty();
        assertThat(repository.findById("old")).isPresent();
        assertThat(repository.findById("keep").orElseThrow().getTrainingLoad()).isEqualTo(99.0);
    }

    @Test
    void reconcileDeletesNothingWhenIntervalsIsDown() {
        source.add(activity("keep", LocalDate.of(2026, 11, 1), 50.0));
        sync.requestImport();

        source.down = true;
        sync.requestReconcile();

        assertThat(repository.findById("keep")).isPresent();
    }

    @Test
    void reconcileCatchesUpImportThatFailedOnStart() {
        source.add(activity("i1", LocalDate.of(2025, 2, 1), 50.0));
        source.down = true;
        sync.requestImport();
        assertThat(repository.count()).isZero();

        source.down = false;
        sync.requestReconcile();

        assertThat(repository.findById("i1")).isPresent();
    }

    // ---- Testdaten --------------------------------------------------------------------------------

    private static IntervalsActivity activity(String id, LocalDate date, double load) {
        return new IntervalsActivity(ActivityId.of(id), "Fahrt " + id, date.atTime(9, 0), "Ride", 30_000.0, 3600,
                3700, 180.0, 200.0, 600.0, 140.0, 170.0, 8.3, 250.0, load, 0.75, null);
    }

    private static ActivityDetected detected(String id, LocalDate date) {
        return new ActivityDetected(ActivityId.of(id), "Fahrt " + id, date.atTime(9, 0), "Ride", 30_000.0, 3600, 3700,
                180.0, 200.0, 600.0, 140.0, 170.0, 8.3, 250.0, null);
    }

    static class FakeSource implements RideSource {
        final Map<String, IntervalsActivity> activities = new HashMap<>();
        final Set<String> failingZones = new HashSet<>();
        final List<String> zoneCalls = new ArrayList<>();
        final List<String> requestedRanges = new ArrayList<>();
        boolean down;

        void add(IntervalsActivity a) {
            activities.put(a.id().value(), a);
        }

        void remove(String id) {
            activities.remove(id);
        }

        @Override
        public List<IntervalsActivity> activities(LocalDate from, LocalDate to) {
            if (down) throw new IntervalsUnavailableException("down", null);
            requestedRanges.add(from + ".." + to);
            return activities.values().stream()
                    .filter(a -> {
                        LocalDate d = a.startDateLocal().toLocalDate();
                        return !d.isBefore(from) && !d.isAfter(to);
                    })
                    .toList();
        }

        @Override
        public Optional<IntervalsActivityZones> zones(ActivityId activityId) {
            zoneCalls.add(activityId.value());
            if (failingZones.contains(activityId.value())) return Optional.empty();
            return Optional.of(new IntervalsActivityZones(
                    List.of(new IntervalsZoneShare("Z1", "Recovery", 600, 25.0, "#aaa"),
                            new IntervalsZoneShare("Z2", "Endurance", 1800, 75.0, "#bbb")),
                    List.of(new IntervalsZoneShare("Z1", "", 1200, 100.0, null)),
                    false, 250));
        }
    }
}
