package com.cycling.training.api;

import java.util.List;

/**
 * Neue Schwellenwerte und Zonen für intervals.icu. Felder, die null sind (Zonen: leere Liste), bleiben
 * unverändert.
 *
 * @param ftp          FTP outdoor in Watt (50–700)
 * @param indoorFtp    FTP indoor in Watt (50–700)
 * @param lthr         Laktatschwellen-HF in bpm (80–230, unter HFmax)
 * @param maxHr        maximale HF in bpm (100–240)
 * @param weight       Gewicht in kg (30–200)
 * @param powerZones   Obergrenzen der Leistungszonen in % FTP, aufsteigend, ohne die offene letzte Zone
 * @param hrZones      Obergrenzen der HF-Zonen in % LTHR, aufsteigend, ohne die letzte Zone
 * @param sweetSpotMin Sweet-Spot-Untergrenze in % FTP (nur zusammen mit sweetSpotMax)
 * @param sweetSpotMax Sweet-Spot-Obergrenze in % FTP
 */
public record AthleteThresholdsUpdateDto(
        Integer ftp,
        Integer indoorFtp,
        Integer lthr,
        Integer maxHr,
        Double weight,
        List<Integer> powerZones,
        List<Integer> hrZones,
        Integer sweetSpotMin,
        Integer sweetSpotMax
) {
}
