package com.cycling.training.api;

/**
 * Neue Schwellenwerte für intervals.icu. Felder, die null sind, bleiben unverändert.
 *
 * @param ftp       FTP outdoor in Watt (50–700)
 * @param indoorFtp FTP indoor in Watt (50–700)
 * @param lthr      Laktatschwellen-HF in bpm (80–230, unter HFmax)
 * @param maxHr     maximale HF in bpm (100–240)
 * @param weight    Gewicht in kg (30–200)
 */
public record AthleteThresholdsUpdateDto(
        Integer ftp,
        Integer indoorFtp,
        Integer lthr,
        Integer maxHr,
        Double weight
) {
}
