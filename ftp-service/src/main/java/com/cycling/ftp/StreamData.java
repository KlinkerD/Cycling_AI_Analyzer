package com.cycling.ftp;

import java.util.List;

/**
 * Die Streams einer Fahrt, soweit der ftp-service sie braucht (Ausschnitt aus dem {@code StreamDataDto}
 * des report-service). Alle Listen sind indexgleich; einzelne Werte koennen null sein.
 *
 * @param time      Sekunden ab Fahrtbeginn
 * @param watts     Leistung in Watt
 * @param heartrate Herzfrequenz in bpm
 */
public record StreamData(
        List<Integer> time,
        List<Integer> watts,
        List<Integer> heartrate
) {
}
