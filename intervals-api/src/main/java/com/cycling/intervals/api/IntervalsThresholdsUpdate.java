package com.cycling.intervals.api;

import java.util.ArrayList;
import java.util.List;

/**
 * Neue Schwellenwerte für die Rad-Einstellungen und das Gewicht in intervals.icu.
 * Felder, die null sind, bleiben unverändert.
 *
 * @param ftp       FTP outdoor in Watt ({@code ftp})
 * @param indoorFtp FTP indoor in Watt ({@code indoor_ftp})
 * @param lthr      Laktatschwellen-HF in bpm ({@code lthr})
 * @param maxHr     maximale HF in bpm ({@code max_hr})
 * @param weight    Gewicht in kg ({@code icu_weight})
 */
public record IntervalsThresholdsUpdate(Integer ftp, Integer indoorFtp, Integer lthr, Integer maxHr, Double weight) {

    /** Gibt es etwas für die Rad-Einstellungen (sport-settings)? */
    public boolean hasSportSettings() {
        return ftp != null || indoorFtp != null || lthr != null || maxHr != null;
    }

    /** Ändert sich etwas, auf dem die HF-Zonen beruhen? */
    public boolean changesHeartRate() {
        return lthr != null || maxHr != null;
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
        return out;
    }

    private static void check(List<String> out, String label, Integer value, int min, int max, String unit) {
        if (value != null && (value < min || value > max)) {
            out.add(label + " muss zwischen " + min + " und " + max + " " + unit + " liegen.");
        }
    }
}
