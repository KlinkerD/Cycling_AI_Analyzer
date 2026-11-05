package com.cycling.messaging.contract;

/**
 * ftp-service -> report-service: Ergebnis der FTP-Test-Erkennung fuer eine Fahrt.
 * <ul>
 *   <li>{@code error} gesetzt: technischer Fehler (z. B. Streams nicht abrufbar), alle Werte null.</li>
 *   <li>{@code isFtpDetected = false}: kein 20-Minuten-Block, der als FTP-Test zu deuten ist.</li>
 *   <li>{@code isFtpDetected = true}: alle Werte gesetzt.</li>
 * </ul>
 *
 * @param requestId          aus dem {@link DetectFtpRequest}
 * @param ftp                Watt, 20-Minuten-Mittel * 0,95
 * @param maxHr              bpm, hoechstes 5-Sekunden-Mittel der ganzen Fahrt
 * @param lthr               bpm, mittlere Herzfrequenz im 20-Minuten-Block
 * @param avgPower20Min      Watt, mittlere Leistung im 20-Minuten-Block
 * @param windowStartSeconds Start des 20-Minuten-Blocks in Sekunden ab Fahrtbeginn (time-Stream)
 * @param error              Fehlertext, null wenn die Erkennung gelaufen ist
 */
public record DetectFtpResponse(
        String requestId,
        String activityId,
        boolean isFtpDetected,
        Integer ftp,
        Integer maxHr,
        Integer lthr,
        Integer avgPower20Min,
        Integer windowStartSeconds,
        String error
) {

    public static DetectFtpResponse notDetected(String requestId, String activityId) {
        return new DetectFtpResponse(requestId, activityId, false, null, null, null, null, null, null);
    }

    public static DetectFtpResponse failed(String requestId, String activityId, String error) {
        return new DetectFtpResponse(requestId, activityId, false, null, null, null, null, null, error);
    }
}
