package com.cycling.messaging.contract;

/**
 * Auftrag an den report-service, den Report einer Aktivitaet zu versenden. Der Report reist
 * vollstaendig mit, der report-service ruft den analysis-service nicht zurueck.
 *
 * @param requestId  neu pro Analyse bzw. pro Klick auf "erneut senden"; dieselbe ID zweimal
 *                   (MQTT-Neuzustellung) loest keinen zweiten Versand aus
 * @param activityId Aktivitaet, zu der der Report gehoert (fuer Logs)
 * @param report     fertiger Report-Inhalt
 */
public record ReportDeliveryRequest(String requestId, String activityId, ReportContent report) {
}
