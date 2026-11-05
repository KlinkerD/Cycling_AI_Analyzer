package com.cycling.ftp;

import java.util.List;

/**
 * Erkennt einen 20-Minuten-FTP-Test in einer Fahrt und berechnet daraus FTP, LTHR und MaxHR.
 *
 * <p>Die Streams werden auf ein 1-Sekunden-Raster gelegt (Index = Sekunde laut {@code time}-Stream), damit
 * Aufzeichnungsluecken als fehlende Werte zaehlen statt das Fenster zu verkuerzen. Ausgewertet wird der
 * 20-Minuten-Block mit der hoechsten Durchschnittsleistung. Er gilt als FTP-Test, wenn alle Kriterien passen:
 * <ol>
 *   <li>Daten vollstaendig: hoechstens {@code maxMissingFraction} fehlende Leistungs- und Pulswerte im Block.</li>
 *   <li>Deutlich haerter als der Rest: Block-Leistung &gt;= {@code minRestRatio} * mittlere Leistung
 *       ausserhalb des Blocks (Nullwerte, also Rollen, zaehlen dort nicht mit).</li>
 *   <li>Ausbelastung: mittlerer Puls der letzten {@code finalSeconds} des Blocks
 *       &gt;= {@code minFinalHrFraction} * MaxHR der Fahrt.</li>
 * </ol>
 * Ohne Puls gibt es keine Erkennung, damit sind LTHR und MaxHR bei einem erkannten Test immer gesetzt.
 */
public class FtpDetector {

    private final FtpDetectionSettings settings;

    public FtpDetector(FtpDetectionSettings settings) {
        this.settings = settings;
    }

    public FtpTestResult detect(StreamData streams) {
        Grid grid = Grid.of(streams);
        int w = settings.windowSeconds();
        if (grid == null || grid.size < w) {
            return FtpTestResult.notDetected("Fahrt kuerzer als " + w + " s oder ohne Zeit-Stream");
        }
        int minPresent = minPresent(w);

        // Block mit der hoechsten Durchschnittsleistung, nur ueber ausreichend vollstaendige Fenster
        int best = -1;
        double bestAvg = -1;
        for (int s = 0; s + w <= grid.size; s++) {
            int count = grid.powerCount(s, s + w);
            if (count < minPresent) continue;
            double avg = (double) grid.powerSum(s, s + w) / count;
            if (avg > bestAvg) {
                bestAvg = avg;
                best = s;
            }
        }
        if (best < 0) {
            return FtpTestResult.notDetected("kein " + w + "-s-Block mit ausreichend Leistungsdaten");
        }
        int end = best + w;

        // Kriterium: deutlich haerter als der Rest der Fahrt
        long restSum = grid.powerSum(0, grid.size) - grid.powerSum(best, end);
        int restMoving = grid.movingCount(0, grid.size) - grid.movingCount(best, end);
        if (restMoving == 0) {
            return FtpTestResult.notDetected("keine Leistungsdaten ausserhalb des Blocks zum Vergleich");
        }
        double restAvg = (double) restSum / restMoving;
        if (bestAvg < restAvg * settings.minRestRatio()) {
            return FtpTestResult.notDetected(String.format(
                    "Block (%.0f W) nicht deutlich haerter als der Rest (%.0f W)", bestAvg, restAvg));
        }

        // Puls: MaxHR der Fahrt, LTHR im Block, Ausbelastung am Ende des Blocks
        Double maxHr = grid.maxHeartRate(settings.maxHrSmoothingSeconds());
        if (maxHr == null) {
            return FtpTestResult.notDetected("keine Herzfrequenz");
        }
        int hrCount = grid.hrCount(best, end);
        if (hrCount < minPresent) {
            return FtpTestResult.notDetected("zu wenig Herzfrequenzdaten im Block");
        }
        double lthr = (double) grid.hrSum(best, end) / hrCount;

        int finalStart = end - settings.finalSeconds();
        int finalCount = grid.hrCount(finalStart, end);
        if (finalCount < minPresent(settings.finalSeconds())) {
            return FtpTestResult.notDetected("zu wenig Herzfrequenzdaten am Ende des Blocks");
        }
        double finalHr = (double) grid.hrSum(finalStart, end) / finalCount;
        if (finalHr < maxHr * settings.minFinalHrFraction()) {
            return FtpTestResult.notDetected(String.format(
                    "keine Ausbelastung: Puls am Blockende %.0f bpm, MaxHR %.0f bpm", finalHr, maxHr));
        }

        return FtpTestResult.detected(
                (int) Math.round(bestAvg * settings.ftpFactor()),
                (int) Math.round(maxHr),
                (int) Math.round(lthr),
                (int) Math.round(bestAvg),
                grid.offset + best);
    }

    private int minPresent(int seconds) {
        return (int) Math.ceil(seconds * (1 - settings.maxMissingFraction()));
    }

    /** Streams auf 1-Sekunden-Raster mit Praefixsummen, damit jedes Fenster in O(1) auswertbar ist. */
    private static final class Grid {
        final int offset;
        final int size;
        final long[] powerSum;
        final int[] powerCount;
        final int[] movingCount;
        final long[] hrSum;
        final int[] hrCount;
        final Integer[] hr;

        private Grid(int offset, Integer[] power, Integer[] hr) {
            this.offset = offset;
            this.size = power.length;
            this.hr = hr;
            powerSum = new long[size + 1];
            powerCount = new int[size + 1];
            movingCount = new int[size + 1];
            hrSum = new long[size + 1];
            hrCount = new int[size + 1];
            for (int i = 0; i < size; i++) {
                Integer p = power[i];
                Integer h = hr[i];
                powerSum[i + 1] = powerSum[i] + (p == null ? 0 : p);
                powerCount[i + 1] = powerCount[i] + (p == null ? 0 : 1);
                movingCount[i + 1] = movingCount[i] + (p != null && p > 0 ? 1 : 0);
                hrSum[i + 1] = hrSum[i] + (h == null ? 0 : h);
                hrCount[i + 1] = hrCount[i] + (h == null ? 0 : 1);
            }
        }

        static Grid of(StreamData streams) {
            if (streams == null || streams.time() == null || streams.time().isEmpty()) return null;
            List<Integer> time = streams.time();
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            for (Integer t : time) {
                if (t == null) continue;
                min = Math.min(min, t);
                max = Math.max(max, t);
            }
            if (min == Integer.MAX_VALUE) return null;
            int size = max - min + 1;
            Integer[] power = new Integer[size];
            Integer[] hr = new Integer[size];
            for (int i = 0; i < time.size(); i++) {
                Integer t = time.get(i);
                if (t == null) continue;
                power[t - min] = valid(at(streams.watts(), i));
                hr[t - min] = valid(at(streams.heartrate(), i));
            }
            return new Grid(min, power, hr);
        }

        private static Integer at(List<Integer> values, int i) {
            return values == null || i >= values.size() ? null : values.get(i);
        }

        /** Negative Werte sind Messfehler und zaehlen als fehlend. */
        private static Integer valid(Integer value) {
            return value == null || value < 0 ? null : value;
        }

        long powerSum(int from, int to) {
            return powerSum[to] - powerSum[from];
        }

        int powerCount(int from, int to) {
            return powerCount[to] - powerCount[from];
        }

        int movingCount(int from, int to) {
            return movingCount[to] - movingCount[from];
        }

        long hrSum(int from, int to) {
            return hrSum[to] - hrSum[from];
        }

        int hrCount(int from, int to) {
            return hrCount[to] - hrCount[from];
        }

        /**
         * Hoechstes gleitendes Puls-Mittel ueber {@code seconds} lueckenlose Sekunden; glaettet einzelne
         * Sensor-Ausreisser. Null, wenn es keinen solchen Abschnitt gibt.
         */
        Double maxHeartRate(int seconds) {
            Double max = null;
            for (int s = 0; s + seconds <= size; s++) {
                if (hrCount(s, s + seconds) < seconds) continue;
                double avg = (double) hrSum(s, s + seconds) / seconds;
                if (max == null || avg > max) max = avg;
            }
            return max;
        }
    }
}
