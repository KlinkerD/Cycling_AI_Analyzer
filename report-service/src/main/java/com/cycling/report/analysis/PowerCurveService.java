package com.cycling.report.analysis;

import com.cycling.types.ActivityId;

import com.cycling.report.api.PowerCurveDto;
import com.cycling.report.api.PowerCurvePointDto;
import com.cycling.report.api.PowerCurveSetDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

/**
 * Berechnet, persistiert und liefert die "Bestleistungs-Kurve" (Mean Maximal Power)
 * pro Aktivitaet, projeziert auf ein festes log-skaliertes Bucket-Schema von 1s bis 2h.
 *
 * Datenquelle: intervals.icu (siehe {@link IntervalsData#getActivityPowerCurve(String)}).
 */
@Service
public class PowerCurveService {

    private static final Logger log = LoggerFactory.getLogger(PowerCurveService.class);

    /**
     * Standard-Buckets in Sekunden (log-skaliert, 24 Stuetzpunkte, 1s..2h).
     * Aenderungen hier bedeuten, dass alte Power-Curves neu eingespielt werden muessen.
     */
    public static final int[] STANDARD_BUCKETS = new int[]{
            1, 2, 5, 10, 15, 20, 30, 45,
            60, 90, 120, 180, 300, 480, 600, 900, 1200, 1800, 2700,
            3600, 4500, 5400, 6300, 7200
    };

    private final IntervalsData intervalsClient;
    private final ActivityAnalysisRepository repository;

    public PowerCurveService(IntervalsData intervalsClient, ActivityAnalysisRepository repository) {
        this.intervalsClient = intervalsClient;
        this.repository = repository;
    }

    /**
     * Holt die Power-Curve fuer die gegebene Aktivitaet von intervals.icu und projeziert sie
     * auf das Standard-Bucket-Schema. Liefert eine Liste mit genau {@link #STANDARD_BUCKETS}.length
     * Eintraegen, wobei {@code watts} null sein kann, wenn der Bucket nicht abgedeckt ist.
     */
    public List<PowerCurvePoint> buildPointsForActivity(ActivityId activityId) {
        SortedMap<Integer, Integer> raw = intervalsClient.getActivityPowerCurve(activityId);
        return interpolate(raw);
    }

    /**
     * Lazy Backfill: Stellt sicher, dass jede uebergebene Aktivitaet eine Power-Curve in der DB hat.
     * Fehlende Kurven werden von intervals.icu nachgeladen, persistiert und in der Entity gesetzt.
     * Fail-soft: Bei Fehlern wird die Aktivitaet einfach uebersprungen (Liste bleibt leer).
     */
    public void ensurePowerCurves(List<ActivityAnalysis> analyses) {
        if (analyses == null) return;
        for (ActivityAnalysis a : analyses) {
            if (a.getPowerCurve() != null && !a.getPowerCurve().isEmpty()) {
                continue;
            }
            try {
                List<PowerCurvePoint> pts = buildPointsForActivity(a.getActivityId());
                a.replacePowerCurve(pts);
                repository.save(a);
                log.info("Power-Curve fuer Aktivitaet {} ({}) nachgeladen", a.getActivityId(), a.getActivityName());
            } catch (Exception e) {
                log.warn("Power-Curve-Backfill fuer Aktivitaet {} fehlgeschlagen: {}", a.getActivityId(), e.getMessage());
            }
        }
    }

    /**
     * Baut den DTO-Satz fuer den Report: aktuelle Aktivitaet + bis zu 3 vorherige
     * (zeitlich davor, neueste zuerst).
     */
    public PowerCurveSetDto buildReportSet(ActivityAnalysis current,
                                           List<ActivityAnalysis> previousNewestFirst,
                                           Integer ftpWatts) {
        return new PowerCurveSetDto(
                toDto(current),
                previousNewestFirst.stream().map(this::toDto).toList(),
                ftpWatts
        );
    }

    private PowerCurveDto toDto(ActivityAnalysis a) {
        List<PowerCurvePointDto> pts = new ArrayList<>();
        if (a.getPowerCurve() != null) {
            for (PowerCurvePoint p : a.getPowerCurve()) {
                pts.add(new PowerCurvePointDto(p.getDurationSeconds(), p.getWatts()));
            }
        }
        return new PowerCurveDto(a.getActivityId(), a.getActivityName(), a.getActivityDate(), pts);
    }

    /**
     * Interpoliert/snappt die rohen (Sekunden -> Watt)-Punkte auf die Standard-Buckets.
     * Strategie:
     * - Exakter Treffer wird uebernommen.
     * - Sonst lineare Interpolation zwischen den naechsten kleineren/groesseren Stuetzstellen.
     * - Liegt der Bucket ausserhalb des verfuegbaren Bereichs (z.B. 2h-Bucket bei 45-min-Aktivitaet),
     *   wird {@code null} eingetragen.
     * - Monotonie-Korrektur: MMP-Kurven sind monoton fallend in t. Falls intervals.icu durch
     *   Rundung mal eine kleine Aufwaertskante liefert, glaetten wir das nach oben hin nicht
     *   (waere semantisch falsch); wir nutzen die Daten as-is.
     */
    List<PowerCurvePoint> interpolate(SortedMap<Integer, Integer> raw) {
        List<PowerCurvePoint> result = new ArrayList<>(STANDARD_BUCKETS.length);
        if (raw == null || raw.isEmpty()) {
            for (int b : STANDARD_BUCKETS) result.add(new PowerCurvePoint(b, null));
            return result;
        }

        int minKey = raw.firstKey();
        int maxKey = raw.lastKey();

        for (int b : STANDARD_BUCKETS) {
            Integer val;
            if (b < minKey || b > maxKey) {
                val = null;
            } else if (raw.containsKey(b)) {
                val = raw.get(b);
            } else {
                // naechste Nachbarn finden
                Integer lower = null, upper = null;
                for (Map.Entry<Integer, Integer> e : raw.entrySet()) {
                    int k = e.getKey();
                    if (k <= b) lower = k;
                    if (k >= b) { upper = k; break; }
                }
                if (lower == null || upper == null) {
                    val = null;
                } else if (lower.equals(upper)) {
                    val = raw.get(lower);
                } else {
                    Integer wL = raw.get(lower);
                    Integer wU = raw.get(upper);
                    if (wL == null || wU == null) {
                        val = (wL != null) ? wL : wU;
                    } else {
                        // Lineare Interpolation in log(t)-Domain (sieht im log-Plot natuerlicher aus).
                        double tL = Math.log(lower);
                        double tU = Math.log(upper);
                        double tB = Math.log(b);
                        double frac = (tB - tL) / (tU - tL);
                        val = (int) Math.round(wL + frac * (wU - wL));
                    }
                }
            }
            result.add(new PowerCurvePoint(b, val));
        }
        return result;
    }
}

