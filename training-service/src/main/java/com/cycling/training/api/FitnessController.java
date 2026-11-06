package com.cycling.training.api;

import com.cycling.intervals.api.IntervalsAthleteThresholds;
import com.cycling.intervals.api.IntervalsAthleteZones;
import com.cycling.intervals.api.IntervalsThresholdsUpdate;
import com.cycling.intervals.api.IntervalsZone;
import com.cycling.intervals.client.IntervalsClient;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * REST-API fuer das Athleten-Profil (Kopfzeile, Overview) und den Fitness-Chart. Die Daten cached der
 * intervals-service; mit {@code refresh=true} werden dessen Caches vorher geleert.
 */
@RestController
@Tag(name = "Fitness")
@RequestMapping("/api/fitness")
public class FitnessController {

    private final IntervalsClient intervals;

    public FitnessController(IntervalsClient intervals) {
        this.intervals = intervals;
    }

    /** GET /api/fitness/athlete - Schwellenwerte und Zonen; fail-soft leer, wenn intervals.icu nicht liefert. */
    @GetMapping("/athlete")
    public ResponseEntity<AthleteZonesDto> getAthlete(
            @RequestParam(required = false, defaultValue = "false") boolean refresh) {
        if (refresh) {
            intervals.evictCachesQuietly();
        }
        IntervalsAthleteZones z = intervals.athleteZonesOrEmpty();
        return ResponseEntity.ok(new AthleteZonesDto(thresholds(z.thresholds()), zones(z.powerZones()),
                zones(z.hrZones()), z.hrReference()));
    }

    /**
     * PUT /api/fitness/athlete/thresholds - schreibt FTP outdoor/indoor, LTHR, HFmax, Gewicht und die Zonen
     * (Leistung in % FTP, HF in % LTHR, Sweet Spot) nach intervals.icu (null-Felder bleiben) und liefert
     * Schwellenwerte und Zonen mit den neuen Werten.
     * 400 mit Begründung bei unplausiblen Werten.
     */
    @PutMapping("/athlete/thresholds")
    public ResponseEntity<AthleteZonesDto> updateThresholds(@RequestBody AthleteThresholdsUpdateDto update) {
        intervals.updateThresholds(new IntervalsThresholdsUpdate(
                update.ftp(), update.indoorFtp(), update.lthr(), update.maxHr(), update.weight(),
                orNull(update.powerZones()), orNull(update.hrZones()), update.sweetSpotMin(), update.sweetSpotMax()));
        IntervalsAthleteZones z = intervals.athleteZones();
        return ResponseEntity.ok(new AthleteZonesDto(thresholds(z.thresholds()), zones(z.powerZones()),
                zones(z.hrZones()), z.hrReference()));
    }

    /** GET /api/fitness/wellness?from&amp;to - Fitness (CTL), Fatigue (ATL) und Form pro Tag, aufsteigend. */
    @GetMapping("/wellness")
    public ResponseEntity<List<WellnessPointDto>> getWellness(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "false") boolean refresh) {
        if (refresh) {
            intervals.evictCachesQuietly();
        }
        return ResponseEntity.ok(intervals.wellness(from, to).stream()
                .map(p -> new WellnessPointDto(p.date(), p.ctl(), p.atl(), p.form()))
                .toList());
    }

    /** Leere oder fehlende Liste = Zonen unverändert lassen. */
    private static List<Integer> orNull(List<Integer> limits) {
        return limits == null || limits.isEmpty() ? null : limits;
    }

    private static AthleteThresholdsDto thresholds(IntervalsAthleteThresholds t) {
        return new AthleteThresholdsDto(t.name(), t.ftp(), t.indoorFtp(), t.lthr(), t.maxHr(), t.weight());
    }

    private static List<ZoneDto> zones(List<IntervalsZone> zones) {
        return zones.stream()
                .map(z -> new ZoneDto(z.index(), z.name(), z.lowerPct(), z.upperPct(), z.lowerValue(),
                        z.upperValue(), z.color()))
                .toList();
    }
}
