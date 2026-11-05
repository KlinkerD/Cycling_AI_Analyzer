package com.cycling.stats.sync;

import com.cycling.intervals.api.IntervalsActivity;
import com.cycling.intervals.api.IntervalsActivityZones;
import com.cycling.intervals.api.IntervalsZoneShare;
import com.cycling.intervals.client.IntervalsUnavailableException;
import com.cycling.messaging.contract.ActivityDetected;
import com.cycling.stats.store.RideRecord;
import com.cycling.stats.store.RideRepository;
import com.cycling.stats.store.ZoneTime;
import com.cycling.types.ActivityId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Haelt die Fahrten in der eigenen Datenbank aktuell. Alle Arbeit laeuft nacheinander auf {@code worker}
 * (ein Thread), damit sich Erstimport, MQTT-Meldungen und Abgleich nicht in die Quere kommen und der
 * MQTT-Thread nie blockiert.
 * <ul>
 *   <li><b>Erstimport</b> beim Start: alle Fahrten ab dem 1.1. des Vorjahres. Fahrten, die schon mit
 *       Zonen gespeichert sind, werden uebersprungen; ein abgebrochener Import macht beim naechsten
 *       Start dort weiter.</li>
 *   <li><b>ActivityDetected</b>: neue Fahrten werden mit Zonen gespeichert. Der intervals-service meldet
 *       bei jedem Lauf alle Fahrten der letzten Tage erneut; bekannte Fahrten werden dann nicht neu geladen.</li>
 *   <li><b>Abgleich</b> (nachts): die letzten Tage komplett neu laden, in intervals.icu geloeschte Fahrten
 *       entfernen und fehlende Zonen nachholen.</li>
 * </ul>
 */
public class RideSync {

    private static final Logger log = LoggerFactory.getLogger(RideSync.class);

    private final RideSource source;
    private final RideRepository repository;
    private final Clock clock;
    private final Executor worker;
    private final int importYearsBack;
    private final int reconcileDays;

    private volatile boolean importRunning;
    /** Nur auf dem Worker gelesen und geschrieben. */
    private boolean importComplete;

    /**
     * @param importYearsBack Erstimport ab dem 1.1. dieses Jahres minus so vieler Jahre (1 = Vorjahr)
     * @param reconcileDays   so viele Tage bis heute gleicht der naechtliche Abgleich ab
     */
    public RideSync(RideSource source, RideRepository repository, Clock clock, Executor worker,
                    int importYearsBack, int reconcileDays) {
        if (importYearsBack < 0) throw new IllegalArgumentException("importYearsBack muss >= 0 sein");
        if (reconcileDays < 1) throw new IllegalArgumentException("reconcileDays muss >= 1 sein");
        this.source = source;
        this.repository = repository;
        this.clock = clock;
        this.worker = worker;
        this.importYearsBack = importYearsBack;
        this.reconcileDays = reconcileDays;
    }

    // ---- Auftraege (laufen auf dem Worker) -------------------------------------------------------

    public void requestImport() {
        importRunning = true;
        worker.execute(() -> run("Erstimport", () -> {
            try {
                importHistory();
            } finally {
                importRunning = false;
            }
        }));
    }

    public void onActivityDetected(ActivityDetected message) {
        worker.execute(() -> run("ActivityDetected", () -> syncDetected(message)));
    }

    /** Nachtabgleich; holt vorher den Erstimport nach, falls der beim Start gescheitert ist. */
    public void requestReconcile() {
        worker.execute(() -> run("Abgleich", () -> {
            if (!importComplete) {
                importHistory();
            }
            reconcile();
        }));
    }

    /** true, solange der Erstimport laeuft oder noch aussteht. */
    public boolean importRunning() {
        return importRunning;
    }

    /** Erster Tag des Erstimports. */
    public LocalDate importStart() {
        return LocalDate.of(today().getYear() - importYearsBack, 1, 1);
    }

    // ---- Ablauf -----------------------------------------------------------------------------------

    void importHistory() {
        LocalDate from = importStart();
        LocalDate to = today();
        List<IntervalsActivity> activities = source.activities(from, to);
        int loaded = 0;
        for (IntervalsActivity activity : activities) {
            if (activity.id() == null || activity.startDateLocal() == null) continue;
            Optional<RideRecord> existing = repository.findById(activity.id().value());
            if (existing.isPresent() && existing.get().isZonesLoaded()) continue;
            save(activity, existing.orElse(null));
            loaded++;
        }
        importComplete = true;
        log.info("Erstimport {} bis {}: {} Fahrten bei intervals.icu, {} neu geladen", from, to,
                activities.size(), loaded);
    }

    void syncDetected(ActivityDetected message) {
        ActivityId id = message.activityId();
        if (id == null || message.startDateLocal() == null) {
            log.warn("ActivityDetected ohne activityId oder Startzeit verworfen");
            return;
        }
        Optional<RideRecord> existing = repository.findById(id.value());
        if (existing.isPresent() && existing.get().isZonesLoaded()) {
            log.debug("Fahrt {} bereits gespeichert", id);
            return;
        }
        // Die Meldung hat keine Trainingslast: die Fahrt daher aus der Aktivitaetsliste des Tages holen.
        LocalDate day = message.startDateLocal().toLocalDate();
        IntervalsActivity activity = source.activities(day, day).stream()
                .filter(a -> id.equals(a.id()))
                .findFirst()
                .orElseGet(() -> fromMessage(message));
        save(activity, existing.orElse(null));
        log.info("Fahrt '{}' ({}) vom {} gespeichert", message.name(), id, day);
    }

    void reconcile() {
        LocalDate to = today();
        LocalDate from = to.minusDays(reconcileDays - 1L);
        List<IntervalsActivity> activities = source.activities(from, to);
        Set<String> ids = activities.stream()
                .filter(a -> a.id() != null && a.startDateLocal() != null)
                .map(a -> a.id().value())
                .collect(Collectors.toSet());

        for (IntervalsActivity activity : activities) {
            if (activity.id() == null || activity.startDateLocal() == null) continue;
            save(activity, repository.findById(activity.id().value()).orElse(null));
        }

        List<RideRecord> removed = repository.findByDateBetween(from, to).stream()
                .filter(r -> !ids.contains(r.getActivityId()))
                .toList();
        repository.deleteAll(removed);

        int retried = 0;
        for (RideRecord ride : repository.findByZonesLoadedFalse()) {
            if (loadZones(ride)) {
                repository.save(ride);
                retried++;
            }
        }
        log.info("Abgleich {} bis {}: {} Fahrten aktualisiert, {} entfernt, {} Zonen nachgeholt",
                from, to, ids.size(), removed.size(), retried);
    }

    // ---- Helfer -----------------------------------------------------------------------------------

    private void save(IntervalsActivity a, RideRecord existing) {
        RideRecord ride = existing != null ? existing : new RideRecord(a.id().value());
        ride.setDate(a.startDateLocal().toLocalDate());
        ride.setName(a.name());
        ride.setType(a.type());
        ride.setDistance(orZero(a.distance()));
        ride.setMovingTime(orZero(a.movingTime()));
        ride.setElapsedTime(orZero(a.elapsedTime()));
        ride.setElevationGain(orZero(a.totalElevationGain()));
        ride.setTrainingLoad(orZero(a.trainingLoad()));
        loadZones(ride);
        repository.save(ride);
    }

    /** Laedt die Zonen; bei Fehlern bleiben die bisherigen Zonen und der Status. @return true bei Erfolg */
    private boolean loadZones(RideRecord ride) {
        Optional<IntervalsActivityZones> zones = source.zones(ActivityId.of(ride.getActivityId()));
        ride.setSyncedAt(Instant.now(clock));
        if (zones.isEmpty()) {
            return false;
        }
        ride.setPowerZones(zoneTimes(zones.get().powerZones()));
        ride.setHrZones(zoneTimes(zones.get().hrZones()));
        ride.setZonesLoaded(true);
        return true;
    }

    private static List<ZoneTime> zoneTimes(List<IntervalsZoneShare> shares) {
        if (shares == null) return List.of();
        return shares.stream()
                .filter(s -> s.zoneName() != null)
                .map(s -> new ZoneTime(s.zoneName(), s.zoneLabel(), s.seconds(), s.color()))
                .toList();
    }

    private static IntervalsActivity fromMessage(ActivityDetected m) {
        return new IntervalsActivity(m.activityId(), m.name(), m.startDateLocal(), m.type(), m.distance(),
                m.movingTime(), m.elapsedTime(), m.averageWatts(), m.normalizedPower(), m.maxWatts(),
                m.averageHeartrate(), m.maxHeartrate(), m.averageSpeed(), m.totalElevationGain(), null, null,
                m.startDate());
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private static void run(String what, Runnable task) {
        try {
            task.run();
        } catch (IntervalsUnavailableException e) {
            log.warn("{} abgebrochen, intervals-service liefert nicht: {}", what, e.getMessage());
        } catch (RuntimeException e) {
            log.error("{} fehlgeschlagen", what, e);
        }
    }

    private static double orZero(Double value) {
        return value != null ? value : 0.0;
    }

    private static int orZero(Integer value) {
        return value != null ? value : 0;
    }
}
