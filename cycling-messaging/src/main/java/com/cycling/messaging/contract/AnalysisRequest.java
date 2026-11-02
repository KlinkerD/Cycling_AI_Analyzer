package com.cycling.messaging.contract;

import java.util.List;

/**
 * Auftrag an den ai-service, ein Training zu bewerten.
 *
 * @param requestId Korrelations-ID; kommt unveraendert im {@link AnalysisResult} zurueck
 * @param previous  bis zu 3 vorherige Fahrten fuer den Vergleich (neueste zuerst)
 */
public record AnalysisRequest(
        String requestId,
        WorkoutData workout,
        List<ZoneShare> powerZones,
        List<ZoneShare> hrZones,
        List<WorkoutData> previous,
        int ftp,
        int maxHr
) {
}
