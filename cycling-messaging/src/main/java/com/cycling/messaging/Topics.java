package com.cycling.messaging;

/**
 * Alle MQTT-Topics an einer Stelle. Payload ist immer JSON (siehe {@link MessageCodec}),
 * QoS 1. Die zugehoerigen Nachrichten liegen in {@code com.cycling.messaging.contract}.
 */
public final class Topics {

    private static final String ROOT = "cycling/";

    /** Praefix der KI-Auftraege; der letzte Teil ist die Quelle, z. B. "report" oder "training". */
    private static final String AI_REQUEST_PREFIX = ROOT + "ai/request/";
    /** Praefix der KI-Antworten; der letzte Teil ist die Quelle des Auftrags. */
    private static final String AI_RESULT_PREFIX = ROOT + "ai/result/";
    /** Abo des ai-service: KI-Auftraege aller Quellen ({@code AiRequest}). */
    public static final String AI_REQUEST_ALL = AI_REQUEST_PREFIX + "+";

    /** intervals-service -> report-service: neue Rad-Aktivitaet bei intervals.icu ({@code ActivityDetected}). */
    public static final String INTERVALS_ACTIVITY_DETECTED = ROOT + "intervals/activity/detected";
    /**
     * intervals-service -> alle: heutige Fitness/Fatigue/Form/Ramp haben sich geaendert ({@code WellnessUpdated}).
     * Retained, damit z. B. der training-service beim Start sofort den aktuellen Stand hat.
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

    /** report-service -> ftp-service: Fahrt auf einen 20-Minuten-FTP-Test pruefen ({@code DetectFtpRequest}). */
    public static final String DETECT_FTP_REQUEST = ROOT + "detect/ftp/request";

    /** ftp-service -> report-service: Ergebnis der FTP-Test-Erkennung ({@code DetectFtpResponse}). */
    public static final String DETECT_FTP_RESPONSE = ROOT + "detect/ftp/response";

    private Topics() {
    }

    /** Topic, auf dem die Quelle {@code source} ihre {@code AiRequest}s veroeffentlicht. */
    public static String aiRequest(String source) {
        return AI_REQUEST_PREFIX + source;
    }

    /** Topic, auf dem der ai-service die {@code AiResult}s fuer die Quelle {@code source} veroeffentlicht. */
    public static String aiResult(String source) {
        return AI_RESULT_PREFIX + source;
    }

    /** Topic, auf dem die Quelle {@code source} ihre {@code DeliveryMessage}s veroeffentlicht. */
    public static String deliveryRequest(String source) {
        return DELIVERY_REQUEST_PREFIX + source;
    }

    /** Quelle eines Auftrags (KI oder Versand) = letzter Teil des Topics. */
    public static String source(String topic) {
        return topic.substring(topic.lastIndexOf('/') + 1);
    }

    /** Quelle eines Versandauftrags = letzter Teil des Topics. */
    public static String deliverySource(String topic) {
        return source(topic);
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
