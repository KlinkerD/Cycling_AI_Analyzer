package com.cycling.report.weather;

import com.cycling.report.api.StreamDataDto;
import com.cycling.report.api.WeatherPointDto;
import com.cycling.weather.api.WeatherQuery;
import com.cycling.weather.api.WeatherResult;
import com.cycling.weather.client.WeatherClient;
import com.cycling.weather.client.WeatherUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Wetter einer Fahrt: bei der Analyse beim weather-service holen, mit der Analyse speichern, fuer Report und
 * Vergleich wieder laden. Das Wetter ist ein Extra: Fehler werden geloggt, die Analyse laeuft ohne weiter.
 */
public class RideWeatherService {

    private static final Logger log = LoggerFactory.getLogger(RideWeatherService.class);

    private final WeatherClient client;
    private final WeatherPointRepository repository;

    /** @param client null = Wetter abgeschaltet (keine URL konfiguriert) */
    public RideWeatherService(WeatherClient client, WeatherPointRepository repository) {
        this.client = client;
        this.repository = repository;
    }

    /**
     * Wetter alle 10 Minuten, nur fuer Outdoor-Fahrten mit GPS (siehe {@link WeatherEligibility}) und bekannter
     * Startzeit in UTC. Leer, wenn nicht zutreffend oder der weather-service nicht liefert.
     *
     * @param indoor       Indoor-Kennung laut intervals.icu; null = unbekannt (dann kein Wetter)
     * @param startDateUtc Startzeit in UTC (ISO-8601), z. B. "2026-11-01T07:30:00Z"
     */
    public List<RideWeatherPoint> fetch(String activityId, Boolean indoor, String type, String startDateUtc,
                                        StreamDataDto streams) {
        if (client == null) return List.of();
        if (!WeatherEligibility.isOutdoorWithGps(indoor, type, streams)) {
            log.debug("Kein Wetter fuer {}: indoor={}, type={}, ohne GPS oder Indoor", activityId, indoor, type);
            return List.of();
        }
        Instant start = parse(startDateUtc);
        if (start == null) {
            log.info("Kein Wetter fuer {}: Startzeit in UTC unbekannt ({})", activityId, startDateUtc);
            return List.of();
        }
        List<WeatherSample> samples = WeatherSampler.sample(streams, start);
        if (samples.isEmpty()) return List.of();

        List<WeatherResult.Point> weather;
        try {
            weather = client.points(samples.stream()
                    .map(s -> new WeatherQuery.Point(s.lat(), s.lng(), s.time().toString()))
                    .toList());
        } catch (WeatherUnavailableException e) {
            log.warn("Kein Wetter fuer {}: {}", activityId, e.getMessage());
            return List.of();
        } catch (RuntimeException e) {
            // z. B. unlesbare Antwort: das Wetter darf die Analyse nie scheitern lassen
            log.warn("Kein Wetter fuer {}: unerwarteter Fehler {}", activityId, e.toString());
            return List.of();
        }
        if (weather.size() != samples.size()) {
            log.warn("Kein Wetter fuer {}: {} Punkte angefragt, {} geliefert", activityId, samples.size(),
                    weather.size());
            return List.of();
        }
        List<RideWeatherPoint> out = new ArrayList<>(samples.size());
        for (int i = 0; i < samples.size(); i++) {
            WeatherResult.Point w = weather.get(i);
            out.add(new RideWeatherPoint(samples.get(i), w.temperatureC(), w.windSpeedKmh(), w.windGustKmh(),
                    w.windDirectionDeg(), w.precipitationMmPerHour()));
        }
        log.info("Wetter fuer {}: {} Punkte", activityId, out.size());
        return out;
    }

    /** Ersetzt das gespeicherte Wetter der Fahrt (leer = loeschen, z. B. Neuanalyse ohne Wetter). */
    @Transactional
    public void replace(String activityId, List<RideWeatherPoint> points) {
        repository.deleteByActivityId(activityId);
        if (points == null || points.isEmpty()) return;
        repository.saveAll(points.stream().map(p -> WeatherPoint.of(activityId, p.toDto())).toList());
    }

    /** Gespeichertes Wetter, nach Minute sortiert; leer ohne Wetter. */
    public List<WeatherPointDto> load(String activityId) {
        return repository.findByActivityIdOrderByMinuteOfRideAsc(activityId).stream().map(WeatherPoint::toDto).toList();
    }

    public Optional<WeatherSummary> summary(String activityId) {
        return WeatherSummary.of(load(activityId));
    }

    /** ISO-8601 mit Offset; ohne Offset (so liefert intervals.icu {@code start_date} nicht) als UTC gelesen. */
    static Instant parse(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }
}
