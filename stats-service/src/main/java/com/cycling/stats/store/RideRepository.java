package com.cycling.stats.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface RideRepository extends JpaRepository<RideRecord, String> {

    /** Fahrten von {@code from} bis {@code to}, beide inklusive. */
    List<RideRecord> findByDateBetween(LocalDate from, LocalDate to);

    /** Fahrten, deren Zonenzeiten noch fehlen. */
    List<RideRecord> findByZonesLoadedFalse();

    int countByZonesLoadedFalse();

    @Query("select min(r.date) from RideRecord r")
    LocalDate oldestDate();

    @Query("select max(r.date) from RideRecord r")
    LocalDate newestDate();

    @Query("select max(r.syncedAt) from RideRecord r")
    Instant lastSyncedAt();
}
