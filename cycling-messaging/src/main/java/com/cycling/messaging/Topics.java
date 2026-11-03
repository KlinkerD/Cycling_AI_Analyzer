package com.cycling.messaging;

/**
 * Alle MQTT-Topics an einer Stelle. Payload ist immer JSON (siehe {@link MessageCodec}),
 * QoS 1. Die zugehoerigen Nachrichten liegen in {@code com.cycling.messaging.contract}.
 */
public final class Topics {

    private static final String ROOT = "cycling/";

    /** report-service -> ai-service: Training analysieren ({@code AnalysisRequest}). */
    public static final String AI_ANALYSIS_REQUEST = ROOT + "ai/analysis/request";
    /** ai-service -> report-service: Ergebnis der Analyse ({@code AnalysisResult}). */
    public static final String AI_ANALYSIS_RESULT = ROOT + "ai/analysis/result";

    /** recommendation-service -> ai-service: Text fuer die Tagesempfehlung ({@code RecommendationTextRequest}). */
    public static final String AI_RECOMMENDATION_REQUEST = ROOT + "ai/recommendation/request";
    /** ai-service -> recommendation-service: Text der Tagesempfehlung ({@code RecommendationTextResult}). */
    public static final String AI_RECOMMENDATION_RESULT = ROOT + "ai/recommendation/result";

    /** intervals-service -> report-service: neue Rad-Aktivitaet bei intervals.icu ({@code ActivityDetected}). */
    public static final String INTERVALS_ACTIVITY_DETECTED = ROOT + "intervals/activity/detected";
    /**
     * intervals-service -> alle: heutige Fitness/Fatigue/Form/Ramp haben sich geaendert ({@code WellnessUpdated}).
     * Retained, damit z. B. ein spaeterer Fitness-Service beim Start sofort den aktuellen Stand hat.
     */
    public static final String INTERVALS_WELLNESS_UPDATED = ROOT + "intervals/wellness/updated";

    /** Praefix der Versandauftraege; der letzte Teil ist die Quelle, z. B. "report" oder "fitness". */
    private static final String DELIVERY_REQUEST_PREFIX = ROOT + "delivery/request/";
    /** Abo des delivery-service: Versandauftraege aller Quellen ({@code DeliveryMessage}). */
    public static final String DELIVERY_REQUEST_ALL = DELIVERY_REQUEST_PREFIX + "+";
    /** report-service -> delivery-service: fertiger Report ({@code DeliveryMessage}). */
    public static final String DELIVERY_REQUEST_REPORT = deliveryRequest("report");
    /**
     * delivery-service -> alle: aktive Kanaele und online/offline ({@code DeliveryServiceStatus}).
     * Retained; "offline" setzt der Broker per Last Will, wenn der Service unerwartet wegfaellt.
     */
    public static final String DELIVERY_STATUS = ROOT + "delivery/status";

    private Topics() {
    }

    /** Topic, auf dem die Quelle {@code source} ihre {@code DeliveryMessage}s veroeffentlicht. */
    public static String deliveryRequest(String source) {
        return DELIVERY_REQUEST_PREFIX + source;
    }

    /** Quelle eines Versandauftrags = letzter Teil des Topics. */
    public static String deliverySource(String topic) {
        return topic.substring(topic.lastIndexOf('/') + 1);
    }

    /** MQTT-Filtervergleich: "+" passt auf genau eine Ebene, "#" (am Ende) auf alle restlichen. */
    public static boolean matches(String filter, String topic) {
        String[] f = filter.split("/", -1);
        String[] t = topic.split("/", -1);
        for (int i = 0; i < f.length; i++) {
            if (f[i].equals("#")) return true;
            if (i >= t.length) return false;
            if (!f[i].equals("+") && !f[i].equals(t[i])) return false;
        }
        return f.length == t.length;
    }
}
