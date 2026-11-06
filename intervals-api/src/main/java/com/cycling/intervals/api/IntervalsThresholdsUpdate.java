package com.cycling.intervals.api;

import java.util.ArrayList;
import java.util.List;

/**
 * Neue Schwellenwerte und Zonen für die Rad-Einstellungen und das Gewicht in intervals.icu.
 * Felder, die null sind, bleiben unverändert.
 *
 * @param ftp           FTP outdoor in Watt ({@code ftp})
 * @param indoorFtp     FTP indoor in Watt ({@code indoor_ftp})
 * @param lthr          Laktatschwellen-HF in bpm ({@code lthr})
 * @param maxHr         maximale HF in bpm ({@code max_hr})
 * @param weight        Gewicht in kg ({@code icu_weight})
 * @param powerZones    Obergrenzen der Leistungszonen in % FTP, ohne die nach oben offene letzte Zone
 *                      ({@code power_zones}); die Anzahl muss zu den Zonen in intervals.icu passen
 * @param hrZones       Obergrenzen der HF-Zonen in % LTHR, ohne die letzte Zone; werden beim Schreiben
 *                      in bpm umgerechnet ({@code hr_zones})
 * @param sweetSpotMin  Sweet-Spot-Untergrenze in % FTP ({@code sweet_spot_min}); nur zusammen mit Max
 * @param sweetSpotMax  Sweet-Spot-Obergrenze in % FTP ({@code sweet_spot_max}); nur zusammen mit Min
 */
public record IntervalsThresholdsUpdate(Integer ftp, Integer indoorFtp, Integer lthr, Integer maxHr, Double weight,
                                        List<Integer> powerZones, List<Integer> hrZones,
                                        Integer sweetSpotMin, Integer sweetSpotMax) {

    /** Nur Schwellenwerte, keine Zonen. */
    public static IntervalsThresholdsUpdate thresholds(Integer ftp, Integer indoorFtp, Integer lthr, Integer maxHr,
                                                       Double weight) {
        return new IntervalsThresholdsUpdate(ftp, indoorFtp, lthr, maxHr, weight, null, null, null, null);
    }

    /** Gibt es etwas für die Rad-Einstellungen (sport-settings)? */
    public boolean hasSportSettings() {
        return ftp != null || indoorFtp != null || lthr != null || maxHr != null
                || powerZones != null || hrZones != null || sweetSpotMin != null || sweetSpotMax != null;
    }

    /** Ändert sich etwas, auf dem die HF-Zonen beruhen? */
    public boolean changesHeartRate() {
        return lthr != null || maxHr != null;
    }

    /**
     * Soll intervals.icu die HF-Zonen selbst neu berechnen? Nur, wenn sich LTHR/HFmax ändern und keine
     * eigenen HF-Zonen mitkommen; sonst würden die eigenen Grenzen gleich wieder überschrieben.
     */
    public boolean recalcHrZones() {
        return changesHeartRate() && hrZones == null;
    }

    public boolean isEmpty() {
        return !hasSportSettings() && weight == null;
    }

    /** Verständliche Fehlertexte für unplausible Werte; leer, wenn alles passt. */
    public List<String> problems() {
        List<String> out = new ArrayList<>();
        if (isEmpty()) out.add("Keine Werte zum Speichern angegeben.");
        check(out, "FTP outdoor", ftp, 50, 700, "W");
        check(out, "FTP indoor", indoorFtp, 50, 700, "W");
        check(out, "LTHR", lthr, 80, 230, "bpm");
        check(out, "HFmax", maxHr, 100, 240, "bpm");
        if (weight != null && (weight.isNaN() || weight < 30 || weight > 200)) {
            out.add("Gewicht muss zwischen 30 und 200 kg liegen.");
        }
        if (lthr != null && maxHr != null && lthr >= maxHr) {
            out.add("LTHR muss unter HFmax liegen.");
        }
        checkZones(out, "Leistungszonen", powerZones, 1, 500, "% FTP");
        checkZones(out, "HF-Zonen", hrZones, 30, 150, "% LTHR");
        if ((sweetSpotMin == null) != (sweetSpotMax == null)) {
            out.add("Sweet Spot braucht Unter- und Obergrenze.");
        } else if (sweetSpotMin != null) {
            check(out, "Sweet Spot (von)", sweetSpotMin, 50, 150, "% FTP");
            check(out, "Sweet Spot (bis)", sweetSpotMax, 50, 150, "% FTP");
            if (sweetSpotMin >= sweetSpotMax) out.add("Sweet Spot: Untergrenze muss unter der Obergrenze liegen.");
        }
        return out;
    }

    private static void checkZones(List<String> out, String label, List<Integer> limits, int min, int max,
                                   String unit) {
        if (limits == null) return;
        if (limits.isEmpty()) {
            out.add(label + ": keine Grenzen angegeben.");
            return;
        }
        Integer previous = null;
        for (int i = 0; i < limits.size(); i++) {
            Integer value = limits.get(i);
            if (value == null || value < min || value > max) {
                out.add(label + ": Grenze von Z" + (i + 1) + " muss zwischen " + min + " und " + max + " " + unit
                        + " liegen.");
                return;
            }
            if (previous != null && value <= previous) {
                out.add(label + ": Grenze von Z" + (i + 1) + " muss über der von Z" + i + " liegen.");
                return;
            }
            previous = value;
        }
    }

    private static void check(List<String> out, String label, Integer value, int min, int max, String unit) {
        if (value != null && (value < min || value > max)) {
            out.add(label + " muss zwischen " + min + " und " + max + " " + unit + " liegen.");
        }
    }
}
