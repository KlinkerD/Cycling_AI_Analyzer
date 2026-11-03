package com.cycling.report.analysis;

public enum AnalysisJobStatus {
    /** Auftrag an den ai-service gesendet, Antwort steht aus. */
    PENDING,
    /** Analyse gespeichert, Report abrufbar. */
    DONE,
    /** Fehler oder Timeout; Grund in {@code error}. */
    FAILED
}
