package com.cycling.report.map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Laedt Kacheln per HTTP von einem Kachelserver im Schema {@code .../{z}/{x}/{y}.png}, Standard OpenStreetMap.
 * Gemaess OSM-Nutzungsregeln mit eigenem User-Agent und einem kleinen Cache (PNG-Bytes, LRU), damit "Erneut
 * senden" nicht dieselben Kacheln noch einmal holt. Alle Kacheln eines Bildes werden parallel geladen, mit einer
 * gemeinsamen Zeitgrenze.
 */
public class HttpTileSource implements TileSource {

    private static final Logger log = LoggerFactory.getLogger(HttpTileSource.class);
    private static final int CACHE_SIZE = 256;
    private static final Duration TOTAL_TIMEOUT = Duration.ofSeconds(20);

    private final String urlTemplate;
    private final String userAgent;
    private final String attribution;
    private final HttpClient httpClient;
    private final Map<Tile, byte[]> cache = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Tile, byte[]> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    public HttpTileSource(String urlTemplate, String userAgent, String attribution) {
        this.urlTemplate = urlTemplate;
        this.userAgent = userAgent;
        this.attribution = attribution == null || attribution.isBlank() ? null : attribution;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String attribution() {
        return attribution;
    }

    @Override
    public Map<Tile, BufferedImage> fetch(Collection<Tile> tiles) {
        Map<Tile, byte[]> bytes = new HashMap<>();
        Map<Tile, CompletableFuture<byte[]>> pending = new HashMap<>();
        for (Tile tile : tiles) {
            byte[] cached;
            synchronized (cache) {
                cached = cache.get(tile);
            }
            if (cached != null) {
                bytes.put(tile, cached);
            } else {
                pending.put(tile, download(tile));
            }
        }

        if (!pending.isEmpty()) {
            try {
                CompletableFuture.allOf(pending.values().toArray(CompletableFuture[]::new))
                        .get(TOTAL_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                log.warn("Kartenkacheln nicht vollstaendig geladen: {}", e.toString());
            }
            int failed = 0;
            for (Map.Entry<Tile, CompletableFuture<byte[]>> e : pending.entrySet()) {
                byte[] png = e.getValue().getNow(null);
                if (png == null) {
                    failed++;
                    e.getValue().cancel(true);
                    continue;
                }
                bytes.put(e.getKey(), png);
                synchronized (cache) {
                    cache.put(e.getKey(), png);
                }
            }
            if (failed > 0) log.warn("{} von {} Kartenkacheln fehlen", failed, pending.size());
        }

        Map<Tile, BufferedImage> images = new HashMap<>();
        bytes.forEach((tile, png) -> {
            try {
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
                if (img != null) images.put(tile, img);
            } catch (Exception e) {
                log.debug("Kachel {} nicht lesbar: {}", tile, e.getMessage());
            }
        });
        return images;
    }

    private CompletableFuture<byte[]> download(Tile tile) {
        String url = urlTemplate
                .replace("{z}", String.valueOf(tile.z()))
                .replace("{x}", String.valueOf(tile.x()))
                .replace("{y}", String.valueOf(tile.y()));
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", userAgent)
                .header("Accept", "image/png,image/*")
                .GET()
                .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(resp -> {
                    if (resp.statusCode() / 100 != 2) {
                        log.debug("Kachel {} -> HTTP {}", url, resp.statusCode());
                        return null;
                    }
                    return resp.body();
                })
                .exceptionally(e -> {
                    log.debug("Kachel {} fehlgeschlagen: {}", url, e.getMessage());
                    return null;
                });
    }
}
