package com.cycling.ftp;

/**
 * Schwellwerte der FTP-Test-Erkennung (siehe {@link FtpDetector}).
 *
 * @param windowSeconds          Laenge des Testblocks (20 min = 1200 s)
 * @param maxMissingFraction     hoechstens so viel Anteil fehlender Leistungs- bzw. Pulswerte im Block
 * @param minRestRatio           Block-Leistung muss mindestens das Vielfache der Leistung ausserhalb sein
 * @param finalSeconds           Laenge des Endstuecks des Blocks fuer die Ausbelastungs-Pruefung
 * @param minFinalHrFraction     Puls im Endstueck mindestens dieser Anteil der MaxHR der Fahrt
 * @param ftpFactor              FTP = Block-Leistung * ftpFactor
 * @param maxHrSmoothingSeconds  MaxHR = hoechstes gleitendes Mittel ueber so viele Sekunden
 */
public record FtpDetectionSettings(
        int windowSeconds,
        double maxMissingFraction,
        double minRestRatio,
        int finalSeconds,
        double minFinalHrFraction,
        double ftpFactor,
        int maxHrSmoothingSeconds
) {

    public static final FtpDetectionSettings DEFAULTS = new FtpDetectionSettings(1200, 0.05, 1.25, 300, 0.90, 0.95, 5);

    public FtpDetectionSettings {
        if (windowSeconds <= 0) throw new IllegalArgumentException("windowSeconds muss > 0 sein");
        if (finalSeconds <= 0 || finalSeconds > windowSeconds) {
            throw new IllegalArgumentException("finalSeconds muss zwischen 1 und windowSeconds liegen");
        }
        if (maxMissingFraction < 0 || maxMissingFraction >= 1) {
            throw new IllegalArgumentException("maxMissingFraction muss in [0, 1) liegen");
        }
        if (maxHrSmoothingSeconds <= 0) throw new IllegalArgumentException("maxHrSmoothingSeconds muss > 0 sein");
    }
}
