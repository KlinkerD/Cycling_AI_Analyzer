package com.cycling.intervals;

import com.cycling.intervals.api.IntervalsActivity;
import com.cycling.intervals.api.IntervalsActivityZones;
import com.cycling.intervals.api.IntervalsApi;
import com.cycling.intervals.api.IntervalsAthleteThresholds;
import com.cycling.intervals.api.IntervalsAthleteZones;
import com.cycling.intervals.api.IntervalsErrorCode;
import com.cycling.intervals.api.IntervalsPowerCurve;
import com.cycling.intervals.api.IntervalsStreams;
import com.cycling.intervals.api.IntervalsWellnessPoint;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** REST-API fuer report-, fitness- und recommendation-service (Pfade siehe {@link IntervalsApi}). */
@RestController
public class IntervalsController {

    private final IntervalsService intervals;

    public IntervalsController(IntervalsService intervals) {
        this.intervals = intervals;
    }

    @GetMapping(IntervalsApi.ACTIVITIES)
    public List<IntervalsActivity> activities(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        checkRange(from, to);
        return intervals.activities(from, to);
    }

    @GetMapping(IntervalsApi.ACTIVITY_STREAMS)
    public IntervalsStreams streams(@PathVariable String id) {
        return intervals.streams(id);
    }

    @GetMapping(IntervalsApi.ACTIVITY_ZONES)
    public IntervalsActivityZones activityZones(@PathVariable String id) {
        return intervals.activityZones(id);
    }

    @GetMapping(IntervalsApi.ACTIVITY_POWER_CURVE)
    public IntervalsPowerCurve powerCurve(@PathVariable String id) {
        return intervals.powerCurve(id);
    }

    @GetMapping(IntervalsApi.ATHLETE_THRESHOLDS)
    public IntervalsAthleteThresholds thresholds() {
        return intervals.thresholds();
    }

    @GetMapping(IntervalsApi.ATHLETE_ZONES)
    public IntervalsAthleteZones athleteZones() {
        return intervals.athleteZones();
    }

    @GetMapping(IntervalsApi.WELLNESS)
    public List<IntervalsWellnessPoint> wellness(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        checkRange(from, to);
        return intervals.wellness(from, to);
    }

    @PostMapping(IntervalsApi.CACHE_EVICT)
    public ResponseEntity<Void> evictCaches() {
        intervals.evictAll();
        return ResponseEntity.noContent().build();
    }

    private static void checkRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new IntervalsApiException(IntervalsErrorCode.BAD_REQUEST, "from liegt nach to.");
        }
    }
}
