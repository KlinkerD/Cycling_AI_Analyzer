package com.cycling.report;

import java.util.List;

/** Die fuer einen Report gerenderten Chart-PNGs (nur die, die es wirklich gibt), in Report-Reihenfolge. */
public record ReportCharts(List<Chart> all) {

    public static final String ROUTE_MAP = "routeMapChart";
    public static final String SPEED_ALT = "speedAltChart";
    public static final String POWER_HR = "powerHrChart";
    public static final String CADENCE = "cadenceChart";
    public static final String POWER_ZONES = "powerZonesChart";
    public static final String HR_ZONES = "hrZonesChart";
    public static final String POWER_CURVE = "powerCurveChart";
    public static final String TREND = "trendChart";

    /** key = stabile Kennung (Anhang-Name, wird Mail-CID / Dateiname), title = Ueberschrift im Report. */
    public record Chart(String key, String title, byte[] png) {
    }

    public Chart get(String key) {
        return all.stream().filter(c -> c.key().equals(key)).findFirst().orElse(null);
    }
}
