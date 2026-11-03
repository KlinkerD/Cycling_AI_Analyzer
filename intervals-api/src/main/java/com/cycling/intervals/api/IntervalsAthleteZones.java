package com.cycling.intervals.api;

import java.util.List;

/**
 * Zonen des Athleten.
 *
 * @param hrReference "lthr" oder "max_hr": worauf sich die HF-Zonen beziehen
 */
public record IntervalsAthleteZones(IntervalsAthleteThresholds thresholds, List<IntervalsZone> powerZones,
                                    List<IntervalsZone> hrZones, String hrReference) {
}
