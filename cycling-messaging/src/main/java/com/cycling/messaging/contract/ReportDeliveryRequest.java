package com.cycling.messaging.contract;

/**
 * Auftrag an den report-delivery-service, den Report einer Aktivitaet zu versenden. Den Report
 * selbst holt der Service per REST beim analysis-service.
 *
 * @param requestId neu pro Analyse bzw. pro Klick auf "erneut senden"; dieselbe ID zweimal
 *                  (MQTT-Neuzustellung) loest keinen zweiten Versand aus
 */
public record ReportDeliveryRequest(String requestId, String activityId) {
}
