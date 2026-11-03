package com.cycling.service;

/** Fuer die Aktivitaet gibt es noch keine gespeicherte Analyse. */
public class ReportNotFoundException extends RuntimeException {

    public ReportNotFoundException(String activityId) {
        super("Kein Report für Aktivität " + activityId + " gefunden. Bitte zuerst analysieren.");
    }
}
