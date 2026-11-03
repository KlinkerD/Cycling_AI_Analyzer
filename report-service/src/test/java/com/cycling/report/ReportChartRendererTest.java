package com.cycling.report;

import com.cycling.messaging.contract.ReportContent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportChartRendererTest {

    private final QuickChartService quickChart = mock(QuickChartService.class);
    private final ReportChartRenderer renderer = new ReportChartRenderer(quickChart);

    @Test
    void keepsOnlyRenderedChartsInReportOrder() {
        ReportContent report = ReportFixtures.report("i1");
        when(quickChart.renderSpeedAndAltitude(any())).thenReturn(ReportFixtures.png("speed"));
        when(quickChart.renderPowerAndHr(any())).thenReturn(null);
        when(quickChart.renderCadenceOverDistance(any())).thenReturn(ReportFixtures.png("cadence"));
        when(quickChart.renderZoneDoughnut(eq("Herzfrequenzzonen"), any())).thenReturn(ReportFixtures.png("hr"));
        when(quickChart.renderPowerCurve(any())).thenReturn(ReportFixtures.png("curve"));

        ReportCharts charts = renderer.render(report);

        assertThat(charts.all()).extracting(ReportCharts.Chart::key)
                .containsExactly("speedAltChart", "cadenceChart", "hrZonesChart", "powerCurveChart");
        assertThat(charts.get("cadenceChart")).isNotNull();
        assertThat(charts.get("powerHrChart")).isNull();
    }
}
