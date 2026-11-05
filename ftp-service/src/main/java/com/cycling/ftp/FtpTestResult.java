package com.cycling.ftp;

/**
 * Ergebnis von {@link FtpDetector}. Bei {@code detected = false} sind alle Werte null und {@code reason}
 * sagt, welches Kriterium nicht gepasst hat (nur fuers Log, geht nicht uebers Netz).
 */
public record FtpTestResult(
        boolean detected,
        Integer ftp,
        Integer maxHr,
        Integer lthr,
        Integer avgPower20Min,
        Integer windowStartSeconds,
        String reason
) {

    static FtpTestResult detected(int ftp, int maxHr, int lthr, int avgPower20Min, int windowStartSeconds) {
        return new FtpTestResult(true, ftp, maxHr, lthr, avgPower20Min, windowStartSeconds, null);
    }

    static FtpTestResult notDetected(String reason) {
        return new FtpTestResult(false, null, null, null, null, null, reason);
    }
}
