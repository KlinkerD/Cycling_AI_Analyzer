package com.cycling.report.analysis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Bestimmt, ob eine Fahrt indoor war, und welche FTP fuer sie gilt.
 */
@Service
public class FtpResolver {

    private final int defaultFtp;

    public FtpResolver(@Value("${cycling.ftp:250}") int defaultFtp) {
        this.defaultFtp = defaultFtp;
    }

    /** Indoor = virtuelle Fahrt (Zwift & Co.) oder Fahrt mit gesetztem Trainer-Flag. */
    public static boolean isIndoor(String type, Boolean trainer) {
        return "VirtualRide".equalsIgnoreCase(type) || Boolean.TRUE.equals(trainer);
    }

    /**
     * FTP fuer eine Fahrt, in dieser Reihenfolge:
     * 1. {@code icu_ftp} der Aktivitaet (die FTP, mit der intervals.icu die Fahrt berechnet hat)
     * 2. bei Indoor-Fahrten die Indoor-FTP des Athleten
     * 3. die (Outdoor-)FTP des Athleten
     * 4. der konfigurierte Default ({@code cycling.ftp})
     * Werte &lt;= 0 gelten als nicht gesetzt.
     */
    public int resolve(Integer icuFtp, boolean indoor, AthleteThresholdsDto thresholds) {
        if (isSet(icuFtp)) return icuFtp;
        if (thresholds != null) {
            if (indoor && isSet(thresholds.indoorFtp())) return thresholds.indoorFtp();
            if (isSet(thresholds.ftp())) return thresholds.ftp();
        }
        return defaultFtp;
    }

    private static boolean isSet(Integer watts) {
        return watts != null && watts > 0;
    }
}
