package com.cycling.messaging.contract;

import com.cycling.types.ActivityId;

import java.util.UUID;

/**
 * report-service -> ftp-service: pruefe, ob diese Fahrt einen 20-Minuten-FTP-Test enthaelt.
 * Wird manuell aus dem Frontend ausgeloest.
 *
 * @param requestId  eindeutig pro Lauf; kommt in der {@link DetectFtpResponse} zurueck, damit
 *                   verspaetete Antworten eines alten Laufs verworfen werden koennen
 * @param activityId intervals.icu-Aktivitaet, deren Streams der ftp-service beim report-service holt
 */
public record DetectFtpRequest(
        UUID requestId,
        ActivityId activityId
) {
}
