package com.cycling.weather;

/**
 * Ort auf einem festen Raster. Punkte in derselben Rasterzelle teilen sich eine Modellreihe: feiner als das
 * Wettermodell (ICON-D2 rund 2 km) wird es ohnehin nicht.
 */
record Location(double lat, double lng) {

    /** Rasterweite in Grad (0.02 Grad ~ 2,2 km in Nord-Sued-Richtung). */
    static final double GRID_DEG = 0.02;

    static Location snap(double lat, double lng) {
        return new Location(round(lat), round(lng));
    }

    private static double round(double deg) {
        // erst auf den Rasterindex, dann auf 3 Nachkommastellen: gleiche Zellen haben exakt gleiche Werte
        return Math.round(Math.round(deg / GRID_DEG) * GRID_DEG * 1000.0) / 1000.0;
    }
}
