package com.cycling.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Open-Meteo ohne API-Key. Juengere Zeitraeume kommen aus der Forecast-API im 15-Minuten-Takt
 * ({@code minutely_15}; in Mitteleuropa aus ICON-D2, sonst aus Stundenwerten interpoliert), aeltere aus der
 * Archive-API (ERA5, stuendlich). Mehrere Orte gehen in einem Aufruf (kommagetrennt), in Bloecken zu
 * {@link #MAX_LOCATIONS_PER_CALL}. Bei 429 einmal warten und wiederholen.
 */
class OpenMeteoSource implements WeatherSource {

    private static final Logger log = LoggerFactory.getLogger(OpenMeteoSource.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String VARIABLES =
            "temperature_2m,wind_speed_10m,wind_gusts_10m,wind_direction_10m,precipitation";
    static final int MAX_LOCATIONS_PER_CALL = 50;

    private final RestClient forecast;
    private final RestClient archive;
    private final Clock clock;
    private final int forecastPastDays;
    private final Duration retryDelay;

    /**
     * @param forecastPastDays bis so viele Tage zurueck die Forecast-API (hoechstens 92 laut Open-Meteo)
     * @param retryDelay       Wartezeit vor der Wiederholung nach 429
     */
    OpenMeteoSource(RestClient forecast, RestClient archive, Clock clock, int forecastPastDays, Duration retryDelay) {
        this.forecast = forecast;
        this.archive = archive;
        this.clock = clock;
        this.forecastPastDays = forecastPastDays;
        this.retryDelay = retryDelay;
    }

    @Override
    public List<Series> fetch(List<Location> locations, LocalDate fromUtc, LocalDate toUtc) {
        List<Series> out = new ArrayList<>(locations.size());
        for (int i = 0; i < locations.size(); i += MAX_LOCATIONS_PER_CALL) {
            out.addAll(fetchBlock(locations.subList(i, Math.min(locations.size(), i + MAX_LOCATIONS_PER_CALL)),
                    fromUtc, toUtc));
        }
        return out;
    }

    private List<Series> fetchBlock(List<Location> locations, LocalDate from, LocalDate to) {
        boolean recent = !from.isBefore(LocalDate.now(clock.withZone(ZoneOffset.UTC)).minusDays(forecastPastDays));
        List<Series> out = fetchBlock(locations, from, to, recent);
        if (recent && out.stream().anyMatch(OpenMeteoSource::hasNoValues)) {
            // Die Forecast-API nimmt aeltere Zeitraeume an, liefert dafuer aber nur null (gesehen ab ~56 Tagen)
            log.info("Open-Meteo-Forecast ohne Werte fuer {} bis {}, nehme das Archiv", from, to);
            out = fetchBlock(locations, from, to, false);
        }
        return out;
    }

    private static boolean hasNoValues(Series s) {
        return s.temperatureC().stream().allMatch(java.util.Objects::isNull);
    }

    private List<Series> fetchBlock(List<Location> locations, LocalDate from, LocalDate to, boolean recent) {
        String block = recent ? "minutely_15" : "hourly";
        int step = recent ? 15 : 60;
        RestClient client = recent ? forecast : archive;
        String path = recent ? "/v1/forecast" : "/v1/archive";

        String lat = locations.stream().map(l -> fmt(l.lat())).collect(Collectors.joining(","));
        String lng = locations.stream().map(l -> fmt(l.lng())).collect(Collectors.joining(","));
        String body = getWithRetry(() -> client.get()
                .uri(b -> b.path(path)
                        .queryParam("latitude", lat)
                        .queryParam("longitude", lng)
                        .queryParam(block, VARIABLES)
                        .queryParam("start_date", from.toString())
                        .queryParam("end_date", to.toString())
                        .queryParam("timezone", "GMT")
                        .queryParam("timeformat", "unixtime")
                        .queryParam("wind_speed_unit", "kmh")
                        .build())
                .retrieve()
                .body(String.class));
        log.debug("Open-Meteo {}: {} Orte, {} bis {}", block, locations.size(), from, to);
        return parse(body, block, step, locations.size());
    }

    /** Antwort lesen: ein Objekt bei einem Ort, ein Array bei mehreren. */
    static List<Series> parse(String body, String block, int step, int expected) {
        JsonNode root;
        try {
            root = JSON.readTree(body == null ? "" : body);
        } catch (IOException e) {
            throw new WeatherSourceException(HttpStatus.BAD_GATEWAY, "Open-Meteo-Antwort ist kein JSON", e);
        }
        List<JsonNode> nodes = new ArrayList<>();
        if (root != null && root.isArray()) root.forEach(nodes::add);
        else if (root != null && root.isObject()) nodes.add(root);
        if (nodes.size() != expected) {
            throw new WeatherSourceException(HttpStatus.BAD_GATEWAY,
                    "Open-Meteo lieferte " + nodes.size() + " statt " + expected + " Orte", null);
        }
        List<Series> out = new ArrayList<>(expected);
        for (JsonNode node : nodes) {
            JsonNode data = node.path(block);
            if (!data.isObject()) {
                out.add(Series.empty(step));
                continue;
            }
            List<Long> times = new ArrayList<>();
            data.path("time").forEach(t -> times.add(t.asLong()));
            out.add(new Series(step, times,
                    doubles(data, "temperature_2m"),
                    doubles(data, "wind_speed_10m"),
                    doubles(data, "wind_gusts_10m"),
                    doubles(data, "wind_direction_10m"),
                    doubles(data, "precipitation")));
        }
        return out;
    }

    private String getWithRetry(java.util.function.Supplier<String> call) {
        try {
            return call.get();
        } catch (HttpClientErrorException.TooManyRequests e) {
            log.warn("Open-Meteo: 429, warte {} und wiederhole einmal", retryDelay);
            sleep(retryDelay);
            try {
                return call.get();
            } catch (HttpStatusCodeException | ResourceAccessException again) {
                throw unavailable(again);
            }
        } catch (HttpStatusCodeException | ResourceAccessException e) {
            throw unavailable(e);
        }
    }

    private static WeatherSourceException unavailable(Exception e) {
        if (e instanceof HttpClientErrorException.BadRequest bad) {
            // Open-Meteo erklaert 400 im Feld "reason" (z. B. Zeitraum ausserhalb des Modells)
            return new WeatherSourceException(HttpStatus.BAD_GATEWAY,
                    "Open-Meteo lehnt die Anfrage ab: " + bad.getResponseBodyAsString(), e);
        }
        return new WeatherSourceException(HttpStatus.SERVICE_UNAVAILABLE,
                "Open-Meteo nicht verfuegbar: " + e.getMessage(), e);
    }

    private static List<Double> doubles(JsonNode data, String field) {
        List<Double> out = new ArrayList<>();
        data.path(field).forEach(v -> out.add(v.isNumber() ? v.asDouble() : null));
        return out;
    }

    private static String fmt(double deg) {
        return String.format(Locale.ROOT, "%.3f", deg);
    }

    private static void sleep(Duration d) {
        try {
            Thread.sleep(d);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
