package com.cycling.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * Caffeine-Caches fuer die Overview-Seite.
 * - wellness: TTL 15 min (CTL/ATL/Form pro Zeitraum)
 * - athleteZones: TTL 1 h (FTP/LTHR/MaxHR + Power-/HF-Zonen)
 */
@Configuration
public class CacheConfig {

    public static final String CACHE_WELLNESS = "wellness";
    public static final String CACHE_ATHLETE_ZONES = "athleteZones";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCache wellness = new CaffeineCache(CACHE_WELLNESS,
                Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofMinutes(15))
                        .maximumSize(64)
                        .build());

        CaffeineCache zones = new CaffeineCache(CACHE_ATHLETE_ZONES,
                Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofHours(1))
                        .maximumSize(4)
                        .build());

        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(wellness, zones));
        return manager;
    }
}

