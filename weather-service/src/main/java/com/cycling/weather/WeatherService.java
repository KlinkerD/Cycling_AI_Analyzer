package com.cycling.weather;

import com.cycling.weather.api.WeatherApi;
import com.cycling.weather.api.WeatherQuery;
import com.cycling.weather.api.WeatherResult;
import com.github.benmanes.caffeine.cache.Cache;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Beantwortet eine {@link WeatherQuery}: Punkte auf Rasterzellen legen ({@link Location}), fehlende Zellen in
 * einem Aufruf bei der {@link WeatherSource} holen, Werte zum Zeitpunkt interpolieren.
 *
 * <p>Der Cache (kurz, im Speicher) sorgt dafuer, dass zwei Anfragen kurz hintereinander - der report-service
 * fragt beim Start der Analyse und beim KI-Ergebnis - dieselben Werte bekommen.
 */
class WeatherService {

    static final String SOURCE = "open-meteo";

    private final WeatherSource source;
    private final Cache<CacheKey, Series> cache;

    WeatherService(WeatherSource source, Cache<CacheKey, Series> cache) {
        this.source = source;
        this.cache = cache;
    }

    record CacheKey(Location location, LocalDate fromUtc, LocalDate toUtc) {
    }

    WeatherResult points(WeatherQuery query) {
        List<Parsed> parsed = validate(query);

        LocalDate from = parsed.stream().map(p -> day(p.instant())).min(LocalDate::compareTo).orElseThrow();
        LocalDate to = parsed.stream().map(p -> day(p.instant())).max(LocalDate::compareTo).orElseThrow();

        Map<Location, Series> series = new HashMap<>();
        List<Location> missing = new ArrayList<>();
        for (Location location : new LinkedHashSet<>(parsed.stream().map(Parsed::location).toList())) {
            Series cached = cache.getIfPresent(new CacheKey(location, from, to));
            if (cached != null) series.put(location, cached);
            else missing.add(location);
        }
        if (!missing.isEmpty()) {
            List<Series> fetched = source.fetch(missing, from, to);
            for (int i = 0; i < missing.size(); i++) {
                series.put(missing.get(i), fetched.get(i));
                cache.put(new CacheKey(missing.get(i), from, to), fetched.get(i));
            }
        }

        List<WeatherResult.Point> out = new ArrayList<>(parsed.size());
        for (Parsed p : parsed) {
            WeatherQuery.Point q = p.point();
            out.add(Interpolation.at(series.get(p.location()), q.lat(), q.lng(), q.time()));
        }
        return new WeatherResult(out, SOURCE);
    }

    private record Parsed(WeatherQuery.Point point, Instant instant, Location location) {
    }

    private static List<Parsed> validate(WeatherQuery query) {
        if (query == null || query.points() == null || query.points().isEmpty()) {
            throw new InvalidQueryException("Keine Punkte angegeben");
        }
        if (query.points().size() > WeatherApi.MAX_POINTS) {
            throw new InvalidQueryException("Hoechstens " + WeatherApi.MAX_POINTS + " Punkte pro Anfrage, waren "
                    + query.points().size());
        }
        List<Parsed> out = new ArrayList<>(query.points().size());
        for (int i = 0; i < query.points().size(); i++) {
            WeatherQuery.Point p = query.points().get(i);
            if (p == null) throw new InvalidQueryException("Punkt " + i + " fehlt");
            if (!(p.lat() >= -90 && p.lat() <= 90) || !(p.lng() >= -180 && p.lng() <= 180)) {
                throw new InvalidQueryException("Punkt " + i + ": Koordinaten ausserhalb des gueltigen Bereichs");
            }
            if (p.time() == null) throw new InvalidQueryException("Punkt " + i + ": Zeit fehlt");
            out.add(new Parsed(p, p.time(), Location.snap(p.lat(), p.lng())));
        }
        return out;
    }

    private static LocalDate day(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC).toLocalDate();
    }
}
