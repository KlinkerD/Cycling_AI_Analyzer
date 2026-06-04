package com.cycling.service;

import com.cycling.dto.StreamDataDto;
import com.cycling.dto.TrendDataDto;
import com.cycling.dto.ZoneDistributionDto;
import com.cycling.dto.PowerCurveDto;
import com.cycling.dto.PowerCurvePointDto;
import com.cycling.dto.PowerCurveSetDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Erzeugt PNG-Charts via QuickChart (https://quickchart.io) fuer den Mail-Report.
 * Ein Chart entspricht visuell den Chart.js-Charts im Angular-Frontend.
 */
@Service
public class QuickChartService {

    private static final Logger log = LoggerFactory.getLogger(QuickChartService.class);
    private static final DateTimeFormatter TREND_FMT = DateTimeFormatter.ofPattern("dd.MM.");

    /** Maximale Punktzahl fuer Linien-Charts. Streams werden ggf. downgesamplt. */
    private static final int MAX_POINTS = 200;
    private static final int CHART_WIDTH = 700;
    private static final int CHART_HEIGHT = 320;
    private static final int DOUGHNUT_SIZE = 360;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${quickchart.base-url:https://quickchart.io}")
    private String baseUrl;

    // ---------- public API ----------

    public byte[] renderPowerOverDistance(StreamDataDto streams) {
        return renderLine("Leistung (W)", "Distanz (km)", streams.distance(), streams.watts(),
                "rgba(63,81,181,1)", "rgba(63,81,181,0.2)");
    }

    public byte[] renderHrOverDistance(StreamDataDto streams) {
        return renderLine("Herzfrequenz (bpm)", "Distanz (km)", streams.distance(), streams.heartrate(),
                "rgba(244,67,54,1)", "rgba(244,67,54,0.2)");
    }

    /**
     * Geschwindigkeit ueber Distanz. Quelle ist {@code velocity} in m/s und wird zu km/h umgerechnet.
     */
    public byte[] renderSpeedOverDistance(StreamDataDto streams) {
        if (streams.velocity() == null || streams.velocity().isEmpty()) return null;
        List<Double> speedKmh = new ArrayList<>(streams.velocity().size());
        for (Double v : streams.velocity()) {
            speedKmh.add(v != null ? Math.round(v * 3.6 * 10.0) / 10.0 : null);
        }
        return renderLine("Geschwindigkeit (km/h)", "Distanz (km)", streams.distance(), speedKmh,
                "rgba(255,152,0,1)", "rgba(255,152,0,0.2)");
    }

    public byte[] renderCadenceOverDistance(StreamDataDto streams) {
        return renderLine("Kadenz (rpm)", "Distanz (km)", streams.distance(), streams.cadence(),
                "rgba(76,175,80,1)", "rgba(76,175,80,0.2)");
    }

    public byte[] renderZoneDoughnut(String title, List<ZoneDistributionDto> zones) {
        if (zones == null || zones.isEmpty()) return null;

        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        List<String> colors = new ArrayList<>();
        for (ZoneDistributionDto z : zones) {
            labels.add(z.zoneName() + " " + z.zoneLabel());
            values.add(round1(z.percentage()));
            colors.add(z.color());
        }

        Map<String, Object> dataset = new LinkedHashMap<>();
        dataset.put("data", values);
        dataset.put("backgroundColor", colors);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("labels", labels);
        data.put("datasets", List.of(dataset));

        Map<String, Object> chart = new LinkedHashMap<>();
        chart.put("type", "doughnut");
        chart.put("data", data);
        chart.put("options", Map.of(
                "plugins", Map.of(
                        "title", Map.of("display", true, "text", title),
                        "legend", Map.of("position", "right")
                )
        ));

        return postChart(chart, DOUGHNUT_SIZE, DOUGHNUT_SIZE);
    }

    public byte[] renderTrend(List<TrendDataDto> trend) {
        if (trend == null || trend.size() < 2) return null;

        // Aelteste zuerst (DB liefert DESC)
        List<TrendDataDto> ordered = new ArrayList<>(trend);
        java.util.Collections.reverse(ordered);

        List<String> labels = new ArrayList<>();
        List<Double> power = new ArrayList<>();
        List<Double> hr = new ArrayList<>();
        List<Double> score = new ArrayList<>();
        for (TrendDataDto t : ordered) {
            labels.add(t.activityDate() != null ? t.activityDate().format(TREND_FMT) : "?");
            power.add(t.avgPowerWatts());
            hr.add(t.avgHeartRate());
            score.add(t.overallScore() != null ? t.overallScore().doubleValue() : null);
        }

        List<Map<String, Object>> datasets = new ArrayList<>();
        datasets.add(lineDataset("Ø Leistung (W)", power, "rgba(63,81,181,1)", "rgba(63,81,181,0.1)", "y"));
        datasets.add(lineDataset("Ø HF (bpm)", hr, "rgba(244,67,54,1)", "rgba(244,67,54,0.1)", "y"));
        datasets.add(lineDataset("Score", score, "rgba(76,175,80,1)", "rgba(76,175,80,0.1)", "y1"));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("labels", labels);
        data.put("datasets", datasets);

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("plugins", Map.of("title", Map.of("display", true, "text", "Trainingsfortschritt")));
        options.put("scales", Map.of(
                "y", Map.of("type", "linear", "position", "left"),
                "y1", Map.of("type", "linear", "position", "right", "min", 0, "max", 10,
                        "grid", Map.of("drawOnChartArea", false))
        ));

        Map<String, Object> chart = new LinkedHashMap<>();
        chart.put("type", "line");
        chart.put("data", data);
        chart.put("options", options);

        return postChart(chart, CHART_WIDTH + 100, CHART_HEIGHT + 40);
    }

    // ---------- Power Curve (Bestleistungs-Kurve) ----------

    /**
     * Rendert die Bestleistungs-Kurve fuer den Mail-Report:
     * - Aktuelle Aktivitaet: kraeftige Linie (Indigo)
     * - Bis zu 3 vorherige Aktivitaeten: progressiv blasser (grau)
     * - FTP-Reflinie als gestrichelte horizontale Linie
     */
    public byte[] renderPowerCurve(PowerCurveSetDto set) {
        if (set == null || set.current() == null) return null;
        List<PowerCurvePointDto> currentPts = set.current().points();
        if (currentPts == null || currentPts.isEmpty()) return null;

        // X-Werte (Sekunden) aus aktueller Kurve – Buckets sind fix gleich fuer alle
        List<Integer> xs = new ArrayList<>(currentPts.size());
        for (PowerCurvePointDto p : currentPts) xs.add(p.durationSeconds());

        List<Map<String, Object>> datasets = new ArrayList<>();

        // Vorherige zuerst (damit aktuelle obenauf gezeichnet wird).
        // 'previous' ist neueste-zuerst sortiert -> alpha 0.45, 0.30, 0.18
        double[] alphas = {0.45, 0.30, 0.18};
        if (set.previous() != null) {
            for (int i = 0; i < set.previous().size() && i < alphas.length; i++) {
                PowerCurveDto prev = set.previous().get(i);
                String label = (prev.activityDate() != null
                        ? prev.activityDate().format(TREND_FMT) + " "
                        : "") + safeShort(prev.activityName(), 24);
                datasets.add(curveDataset(label, prev.points(),
                        "rgba(120,120,120," + alphas[i] + ")", 1.5, false));
            }
        }

        // Aktuelle Aktivitaet (deutlich)
        String currentLabel = (set.current().activityDate() != null
                ? set.current().activityDate().format(TREND_FMT) + " "
                : "") + safeShort(set.current().activityName(), 24);
        datasets.add(curveDataset(currentLabel, currentPts, "rgba(63,81,181,1)", 2.5, true));

        // FTP-Reflinie (horizontale Linie ueber gesamten X-Bereich)
        if (set.ftpWatts() != null && set.ftpWatts() > 0) {
            int ftp = set.ftpWatts();
            List<Integer> ftpLine = new ArrayList<>(xs.size());
            for (int ignored : xs) ftpLine.add(ftp);
            Map<String, Object> ftpDs = new LinkedHashMap<>();
            ftpDs.put("label", "FTP " + ftp + " W");
            ftpDs.put("data", ftpLine);
            ftpDs.put("borderColor", "rgba(120,120,120,0.7)");
            ftpDs.put("backgroundColor", "rgba(0,0,0,0)");
            ftpDs.put("borderWidth", 1.0);
            ftpDs.put("borderDash", List.of(6, 4));
            ftpDs.put("pointRadius", 0);
            ftpDs.put("fill", false);
            ftpDs.put("tension", 0);
            datasets.add(ftpDs);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("labels", xs);
        data.put("datasets", datasets);

        // Tick-Callback: Sekunden -> "5s","1m","2h" usw.
        // QuickChart erlaubt JS-Funktionen via "callback" String – als Fallback liefern wir
        // statische Stuetzpunkte ueber 'afterBuildTicks' nicht; stattdessen nutzen wir
        // die "ticks.callback" String-Eval-Variante von QuickChart.
        String tickCallback = "function(value){"
                + "var v=Number(value);"
                + "if(v<60)return v+'s';"
                + "if(v<3600){var m=v/60;return (m%1===0?m:m.toFixed(1))+'m';}"
                + "var h=v/3600;return (h%1===0?h:h.toFixed(1))+'h';}";

        Map<String, Object> xScale = new LinkedHashMap<>();
        xScale.put("type", "logarithmic");
        xScale.put("title", Map.of("display", true, "text", "Dauer"));
        xScale.put("ticks", Map.of(
                "callback", tickCallback,
                "autoSkip", true,
                "maxTicksLimit", 12
        ));

        Map<String, Object> yScale = new LinkedHashMap<>();
        yScale.put("title", Map.of("display", true, "text", "Leistung (W)"));
        yScale.put("beginAtZero", false);

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("plugins", Map.of(
                "title", Map.of("display", true, "text", "Bestleistungen (Mean Maximal Power)"),
                "legend", Map.of("display", true, "position", "bottom")
        ));
        options.put("scales", Map.of("x", xScale, "y", yScale));
        options.put("spanGaps", true);

        Map<String, Object> chart = new LinkedHashMap<>();
        chart.put("type", "line");
        chart.put("data", data);
        chart.put("options", options);

        return postChart(chart, CHART_WIDTH + 100, CHART_HEIGHT + 60);
    }

    private Map<String, Object> curveDataset(String label, List<PowerCurvePointDto> points,
                                             String borderColor, double borderWidth, boolean current) {
        List<Integer> ys = new ArrayList<>(points.size());
        for (PowerCurvePointDto p : points) ys.add(p.watts());

        Map<String, Object> ds = new LinkedHashMap<>();
        ds.put("label", label);
        ds.put("data", ys);
        ds.put("borderColor", borderColor);
        ds.put("backgroundColor", "rgba(0,0,0,0)");
        ds.put("borderWidth", borderWidth);
        ds.put("pointRadius", current ? 2 : 0);
        ds.put("pointHoverRadius", 4);
        ds.put("fill", false);
        ds.put("tension", 0.35);
        ds.put("spanGaps", true);
        return ds;
    }

    private static String safeShort(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, Math.max(0, max - 1)) + "…";
    }

    // ---------- intern ----------

    private byte[] renderLine(String yLabel, String xLabel,
                              List<Double> distanceMeters, List<? extends Number> ySeries,
                              String borderColor, String fillColor) {
        if (ySeries == null || ySeries.isEmpty()) return null;

        // Distanz (m) -> km, auf 2 Nachkommastellen gerundet, Downsample
        List<Double> distKm = new ArrayList<>(distanceMeters.size());
        for (Double d : distanceMeters) {
            distKm.add(d != null ? Math.round(d / 1000.0 * 100.0) / 100.0 : 0.0);
        }

        List<Double> xs = downsampleDouble(distKm, MAX_POINTS);
        List<Double> ys = downsampleNumber(ySeries, MAX_POINTS);

        Map<String, Object> dataset = new LinkedHashMap<>();
        dataset.put("label", yLabel);
        dataset.put("data", ys);
        dataset.put("borderColor", borderColor);
        dataset.put("backgroundColor", fillColor);
        dataset.put("fill", true);
        dataset.put("pointRadius", 0);
        dataset.put("borderWidth", 1.5);
        dataset.put("tension", 0.2);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("labels", xs);
        data.put("datasets", List.of(dataset));

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("plugins", Map.of(
                "title", Map.of("display", true, "text", yLabel + " ueber " + xLabel),
                "legend", Map.of("display", false)
        ));
        options.put("scales", Map.of(
                "x", Map.of(
                        "type", "linear",
                        "title", Map.of("display", true, "text", xLabel)
                ),
                "y", Map.of("title", Map.of("display", true, "text", yLabel))
        ));

        Map<String, Object> chart = new LinkedHashMap<>();
        chart.put("type", "line");
        chart.put("data", data);
        chart.put("options", options);

        return postChart(chart, CHART_WIDTH, CHART_HEIGHT);
    }

    private Map<String, Object> lineDataset(String label, List<Double> values,
                                            String border, String fill, String yAxisId) {
        Map<String, Object> ds = new LinkedHashMap<>();
        ds.put("label", label);
        ds.put("data", values);
        ds.put("borderColor", border);
        ds.put("backgroundColor", fill);
        ds.put("yAxisID", yAxisId);
        ds.put("tension", 0.3);
        ds.put("borderWidth", 2);
        ds.put("pointRadius", 3);
        ds.put("fill", false);
        return ds;
    }

    private byte[] postChart(Map<String, Object> chart, int width, int height) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("chart", chart);
            body.put("width", width);
            body.put("height", height);
            body.put("format", "png");
            body.put("backgroundColor", "white");
            body.put("devicePixelRatio", 1.5);
            byte[] json = mapper.writeValueAsBytes(body);

            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl.replaceAll("/$", "") + "/chart"))
                    .timeout(Duration.ofSeconds(20))
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .header(HttpHeaders.ACCEPT, "image/png")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(json))
                    .build();

            HttpResponse<byte[]> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() / 100 != 2) {
                log.warn("QuickChart Fehler {}: {}", resp.statusCode(),
                        new String(resp.body()).substring(0, Math.min(200, resp.body().length)));
                return null;
            }
            return resp.body();
        } catch (Exception e) {
            log.warn("QuickChart-Aufruf fehlgeschlagen: {}", e.getMessage());
            return null;
        }
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static List<Double> downsampleDouble(List<Double> src, int maxPoints) {
        if (src == null || src.isEmpty()) return List.of();
        if (src.size() <= maxPoints) return src;
        double step = (double) src.size() / maxPoints;
        List<Double> out = new ArrayList<>(maxPoints);
        for (int i = 0; i < maxPoints; i++) {
            int idx = (int) Math.floor(i * step);
            Double v = src.get(idx);
            out.add(v != null ? v : 0.0);
        }
        return out;
    }

    private static List<Double> downsampleNumber(List<? extends Number> src, int maxPoints) {
        if (src == null || src.isEmpty()) return List.of();
        int n = src.size();
        if (n <= maxPoints) {
            List<Double> out = new ArrayList<>(n);
            for (Number v : src) out.add(v != null ? v.doubleValue() : null);
            return out;
        }
        // Bucket-Mittelwert (robuster gegen Nadelspitzen als Subsampling)
        double step = (double) n / maxPoints;
        List<Double> out = new ArrayList<>(maxPoints);
        for (int i = 0; i < maxPoints; i++) {
            int from = (int) Math.floor(i * step);
            int to = (int) Math.floor((i + 1) * step);
            if (to <= from) to = from + 1;
            if (to > n) to = n;
            double sum = 0;
            int count = 0;
            for (int j = from; j < to; j++) {
                Number v = src.get(j);
                if (v != null) {
                    sum += v.doubleValue();
                    count++;
                }
            }
            out.add(count > 0 ? sum / count : null);
        }
        return out;
    }
}

