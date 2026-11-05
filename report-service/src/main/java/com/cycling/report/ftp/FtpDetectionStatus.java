package com.cycling.report.ftp;

public enum FtpDetectionStatus {
    /** Auftrag an den ftp-service gesendet, Antwort steht aus. */
    PENDING,
    /** FTP-Test erkannt, Werte gesetzt. */
    DETECTED,
    /** Kein 20-Minuten-Block, der als FTP-Test zu deuten ist. */
    NOT_DETECTED,
    /** Technischer Fehler oder keine Antwort innerhalb des Timeouts; erneut ausloesbar. */
    FAILED
}
