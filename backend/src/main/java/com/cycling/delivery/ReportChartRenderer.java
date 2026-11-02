package com.cycling.delivery;

import com.cycling.dto.ReportDto;
import com.cycling.service.QuickChartService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Rendert alle Charts eines Reports einmal ueber QuickChart, damit Mail und Discord
 * dieselben PNGs verwenden. Charts ohne Daten (null / leer) werden weggelassen.
 */
@Component
public class ReportChartRenderer {

    private final QuickChartService quickChart;

    public ReportChartRenderer(QuickChartService quickChart) {
        this.quickChart = quickChart;
    }

    public ReportCharts render(ReportDto report) {
        List<ReportCharts.Chart> charts = new ArrayList<>();
        boolean hasCadence = report.streams().cadence() != null && !report.streams().cadence().isEmpty();

        add(charts, ReportCharts.SPEED_ALT, "Geschwindigkeit & Höhe über Distanz",
                quickChart.renderSpeedAndAltitude(report.streams()));
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
