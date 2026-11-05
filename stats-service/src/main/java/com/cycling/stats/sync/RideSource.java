package com.cycling.stats.sync;

import com.cycling.intervals.api.IntervalsActivity;
import com.cycling.intervals.api.IntervalsActivityZones;
import com.cycling.intervals.client.IntervalsClient;
import com.cycling.intervals.client.IntervalsUnavailableException;
import com.cycling.types.ActivityId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Woher die Fahrten kommen: der intervals-service. Eigenes Interface, damit Tests ohne HTTP auskommen. */
public interface RideSource {

    /**
     * Rad-Aktivitaeten von {@code from} bis {@code to}, beide inklusive.
     *
     * @throws IntervalsUnavailableException wenn der intervals-service nicht liefert
     */
    List<IntervalsActivity> activities(LocalDate from, LocalDate to);

    /** Zonenzeiten einer Fahrt; leer, wenn der Abruf scheitert. */
    Optional<IntervalsActivityZones> zones(ActivityId activityId);

    static RideSource of(IntervalsClient client) {
        return new RideSource() {
            @Override
            public List<IntervalsActivity> activities(LocalDate from, LocalDate to) {
                return client.activities(from, to);
            }

            @Override
            public Optional<IntervalsActivityZones> zones(ActivityId activityId) {
                return client.activityZonesIfAvailable(activityId);
            }
        };
    }
}
