package com.cycling.controller;

import com.cycling.config.CacheConfig;
import com.cycling.dto.AthleteZonesDto;
import com.cycling.dto.WellnessPointDto;
import com.cycling.service.IntervalsService;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Endpoints für die Übersichts-/Overview-Seite:
 * - Wellness (CTL/ATL/Form) für Fitness-Chart
 * - Athleten-Zonen (Power + HF + Schwellenwerte)
 *
 * Caching via Caffeine ({@link CacheConfig}). Mit {@code refresh=true} wird der Cache umgangen
 * und für den entsprechenden Schluessel neu befuellt.
 */
@RestController
@RequestMapping("/api/overview")
public class OverviewController {

    private final IntervalsService intervalsService;
    private final CacheManager cacheManager;

    public OverviewController(IntervalsService intervalsService, CacheManager cacheManager) {
        this.intervalsService = intervalsService;
        this.cacheManager = cacheManager;
    }

    @GetMapping("/wellness")
    public ResponseEntity<List<WellnessPointDto>> getWellness(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "false") boolean refresh
    ) {
        String fromStr = from.format(DateTimeFormatter.ISO_DATE);
        String toStr = to.format(DateTimeFormatter.ISO_DATE);
        String key = fromStr + "_" + toStr;

        Cache cache = cacheManager.getCache(CacheConfig.CACHE_WELLNESS);
        if (cache != null && refresh) {
            cache.evict(key);
        }
        @SuppressWarnings("unchecked")
        List<WellnessPointDto> result = cache != null
                ? cache.get(key, () -> intervalsService.getWellness(fromStr, toStr))
                : intervalsService.getWellness(fromStr, toStr);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/zones")
    public ResponseEntity<AthleteZonesDto> getZones(
            @RequestParam(required = false, defaultValue = "false") boolean refresh
    ) {
        Cache cache = cacheManager.getCache(CacheConfig.CACHE_ATHLETE_ZONES);
        String key = "default";
        if (cache != null && refresh) {
            cache.evict(key);
        }
        AthleteZonesDto result = cache != null
                ? cache.get(key, intervalsService::getAthleteZones)
                : intervalsService.getAthleteZones();
        return ResponseEntity.ok(result);
    }
}

