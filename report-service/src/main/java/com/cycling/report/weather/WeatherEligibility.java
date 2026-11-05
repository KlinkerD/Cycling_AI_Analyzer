package com.cycling.report.weather;

import com.cycling.report.api.StreamDataDto;

/**
 * Wann eine Fahrt Wetter bekommt. Nur echte Outdoor-Fahrten: Zwift und Co. ({@code VirtualRide}) haben oft einen
 * GPS-Stream mit Fantasie-Koordinaten (Watopia liegt bei den Salomonen), GPS allein reicht also nicht. Ist die
 * Indoor-Kennung unbekannt (Zonen-Abruf gescheitert), gibt es lieber kein Wetter als falsches.
 */
public final class WeatherEligibility {

    private WeatherEligibility() {
    }

    /**
     * @param indoor Indoor-Kennung laut intervals.icu; null = unbekannt
     * @param type   Aktivitaetstyp, z. B. "Ride" oder "VirtualRide"
     */
    public static boolean isOutdoorWithGps(Boolean indoor, String type, StreamDataDto streams) {
        return Boolean.FALSE.equals(indoor)
                && isOutdoorRideType(type)
                && validGpsPoints(streams) >= 2;
    }

    /** Draussen gefahrene Radtypen laut intervals.icu (Ride, GravelRide, MountainBikeRide, EBikeRide, ...). */
    static boolean isOutdoorRideType(String type) {
        if (type == null) return false;
        String t = type.toLowerCase(java.util.Locale.ROOT);
        return t.endsWith("ride") && !t.equals("virtualride");
    }

    private static int validGpsPoints(StreamDataDto streams) {
        if (streams == null || streams.lat() == null || streams.lng() == null) return 0;
        int count = 0;
        int n = Math.min(streams.lat().size(), streams.lng().size());
        for (int i = 0; i < n && count < 2; i++) {
            if (streams.lat().get(i) != null && streams.lng().get(i) != null) count++;
        }
        return count;
    }
}
