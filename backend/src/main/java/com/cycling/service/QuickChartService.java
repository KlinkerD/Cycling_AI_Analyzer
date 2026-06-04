package com.cycling.service;

import com.cycling.dto.StreamDataDto;
import com.cycling.dto.TrendDataDto;
import com.cycling.dto.ZoneDistributionDto;
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

