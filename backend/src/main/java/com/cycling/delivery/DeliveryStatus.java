package com.cycling.delivery;

public enum DeliveryStatus {
    /** Noch nicht (erfolgreich) gesendet, wird vom Scheduler erneut versucht. */
    PENDING,
    SENT,
    /** Aufgegeben: dauerhafter Fehler oder max. Versuche erreicht. */
    FAILED
}
