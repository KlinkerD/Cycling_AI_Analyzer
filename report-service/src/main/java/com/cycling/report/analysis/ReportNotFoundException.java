package com.cycling.report.analysis;

import com.cycling.types.ActivityId;

/** Fuer die Aktivitaet gibt es noch keine gespeicherte Analyse. */
public class ReportNotFoundException extends RuntimeException {

    public ReportNotFoundException(ActivityId activityId) {
        super("Kein Report für Aktivität " + activityId + " gefunden. Bitte zuerst analysieren.");
    }
}
