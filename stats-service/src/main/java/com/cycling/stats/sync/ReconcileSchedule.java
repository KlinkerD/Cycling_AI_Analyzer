package com.cycling.stats.sync;

import org.springframework.scheduling.annotation.Scheduled;

/** Stoesst den naechtlichen Abgleich an (Zeitpunkt: {@code stats.reconcile.cron}). */
public class ReconcileSchedule {

    private final RideSync sync;

    public ReconcileSchedule(RideSync sync) {
        this.sync = sync;
    }

    @Scheduled(cron = "${stats.reconcile.cron}", zone = "${stats.zone}")
    public void reconcile() {
        sync.requestReconcile();
    }
}
