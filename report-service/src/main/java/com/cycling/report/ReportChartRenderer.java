package com.cycling.report;

import com.cycling.report.map.RouteMapRenderer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Rendert alle Bilder eines Reports einmal: zuerst die Karte mit dem Fahrtverlauf (selbst gezeichnet), dann die
 * Charts ueber QuickChart. Sie gehen als Anhaenge der DeliveryMessage an alle Kanaele. Bilder ohne Daten
 * (null / leer, z. B. die Karte bei Indoor-Fahrten) werden weggelassen.
 */
@Component
public class ReportChartRenderer {

    private final QuickChartService quickChart;
    private final RouteMapRenderer routeMap;

    public ReportChartRenderer(QuickChartService quickChart, RouteMapRenderer routeMap) {
        this.quickChart = quickChart;
        this.routeMap = routeMap;
    }

    public ReportCharts render(ReportContent report) {
        List<ReportCharts.Chart> charts = new ArrayList<>();
        boolean hasCadence = report.streams().cadence() != null && !report.streams().cadence().isEmpty();

        add(charts, ReportCharts.ROUTE_MAP, "Fahrtverlauf",
                routeMap.render(report.streams(), report.powerZoneBands()));
        add(charts, ReportCharts.SPEED_ALT, "Geschwindigkeit & Höhe über Distanz",
                quickChart.renderSpeedAndAltitude(report.streams()));
        add(charts, ReportCharts.WEATHER, "Temperatur & Wind über Distanz",
                quickChart.renderWeather(report.weather()));
        add(charts, ReportCharts.POWER_HR, "Leistung & Herzfrequenz über Distanz",
                quickChart.renderPowerAndHr(report.streams()));
        add(charts, ReportCharts.CADENCE, "Kadenz über Distanz",
                hasCadence ? quickChart.renderCadenceOverDistance(report.streams()) : null);
        add(charts, ReportCharts.POWER_ZONES, "Leistungszonen (FTP-basiert)",
                quickChart.renderZoneDoughnut("Leistungszonen (FTP-basiert)", report.powerZones()));
        add(charts, ReportCharts.HR_ZONES, "Herzfrequenzzonen",
                quickChart.renderZoneDoughnut("Herzfrequenzzonen", report.hrZones()));

        int previousCount = report.powerCurveSet() != null && report.powerCurveSet().previous() != null
                ? report.powerCurveSet().previous().size() : 0;
        add(charts, ReportCharts.POWER_CURVE, "Bestleistungen: aktuelles Training vs. " + previousCount + " vorherige",
                quickChart.renderPowerCurve(report.powerCurveSet()));

        int trendCount = report.trend() != null ? report.trend().size() : 0;
        add(charts, ReportCharts.TREND, "Trainingsfortschritt: letzte " + trendCount + " analysierte Trainings",
                quickChart.renderTrend(report.trend()));

        return new ReportCharts(List.copyOf(charts));
    }

    private static void add(List<ReportCharts.Chart> charts, String key, String title, byte[] png) {
        if (png != null && png.length > 0) {
            charts.add(new ReportCharts.Chart(key, title, png));
        }
    }
}
