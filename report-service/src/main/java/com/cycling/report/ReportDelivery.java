package com.cycling.report;

/**
 * Auftrag, den Report einer Aktivitaet zu versenden (intern zwischen Analyse/Resend und
 * {@link ReportRequestHandler}).
 *
 * @param requestId  neu pro Analyse (Job-ID) bzw. pro Klick auf "erneut senden"; wird zur messageId,
 *                   damit der delivery-service doppelte Auftraege verwirft
 * @param activityId Aktivitaet, zu der der Report gehoert (fuer Logs)
 * @param report     fertiger Report-Inhalt
 */
public record ReportDelivery(String requestId, String activityId, ReportContent report) {
}
