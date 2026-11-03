package com.cycling.intervals;

import com.cycling.intervals.api.IntervalsErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.Semaphore;

/**
 * GET-Aufrufe an intervals.icu mit Basic Auth, Wiederholungen und begrenzter Parallelitaet.
 *
 * <ul>
 *   <li>Wiederholt bei 429, 5xx und Netzwerkfehlern hoechstens {@code maxRetries}-mal (nach 1 s, 3 s, ...).
 *       Bei 429 gilt ein Retry-After von intervals.icu, gedeckelt auf {@code maxRetryAfter}.</li>
 *   <li>401/403/404 werden nicht wiederholt.</li>
 *   <li>Hoechstens {@code maxConcurrent} Anfragen gleichzeitig; gewartet wird ohne belegten Platz.</li>
 * </ul>
 * Alle Fehler kommen als {@link IntervalsApiException} mit passendem Code.
 */
public class IntervalsHttpClient {

    private static final Logger log = LoggerFactory.getLogger(IntervalsHttpClient.class);
    private static final Duration[] BACKOFF = {Duration.ofSeconds(1), Duration.ofSeconds(3), Duration.ofSeconds(5)};

    /** Wartet zwischen zwei Versuchen; in Tests ersetzbar. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final RestClient restClient;
    private final IntervalsProperties properties;
    private final Sleeper sleeper;
    private final Semaphore permits;

    public IntervalsHttpClient(RestClient restClient, IntervalsProperties properties, Sleeper sleeper) {
        this.restClient = restClient;
        this.properties = properties;
        this.sleeper = sleeper;
        this.permits = new Semaphore(Math.max(1, properties.maxConcurrent()), true);
    }

    public String athleteId() {
        return properties.athleteId();
    }

    /**
     * Holt {@code path} (relativ zur Basis-URL, z. B. "/athlete/i1/wellness?oldest=...") als JSON-Text.
     *
     * @throws IntervalsApiException bei fehlender Konfiguration oder wenn intervals.icu nicht liefert
     */
    public String get(String path) {
        if (!properties.isConfigured()) {
            throw new IntervalsApiException(IntervalsErrorCode.INTERVALS_NOT_CONFIGURED,
                    "intervals.icu ist nicht konfiguriert: INTERVALS_API_KEY und INTERVALS_ATHLETE_ID setzen.");
        }
        for (int attempt = 0; ; attempt++) {
            try {
                return execute(path);
            } catch (HttpClientErrorException e) {
                int status = e.getStatusCode().value();
                if (status == 401 || status == 403) {
                    throw new IntervalsApiException(IntervalsErrorCode.INTERVALS_AUTH,
                            "intervals.icu verweigert den Zugriff (" + status + "). Bitte API-Key und Athlete-ID pruefen.",
                            null, e);
                }
                if (status == 404) {
                    throw new IntervalsApiException(IntervalsErrorCode.NOT_FOUND,
                            "intervals.icu kennt " + path + " nicht.", null, e);
                }
                if (status != 429) {
                    throw new IntervalsApiException(IntervalsErrorCode.INTERVALS_UNAVAILABLE,
                            "intervals.icu lehnt die Anfrage ab (" + status + ").", null, e);
                }
                Duration retryAfter = retryAfter(e);
                if (attempt >= properties.maxRetries()) {
                    throw new IntervalsApiException(IntervalsErrorCode.INTERVALS_RATE_LIMIT,
                            "Rate-Limit von intervals.icu erreicht, bitte spaeter erneut versuchen.",
                            retryAfter != null ? retryAfter : backoff(attempt), e);
                }
                Duration wait = retryAfter != null ? min(retryAfter, properties.maxRetryAfter()) : backoff(attempt);
                pause(path, attempt, wait, "429");
            } catch (HttpServerErrorException e) {
                if (attempt >= properties.maxRetries()) {
                    throw new IntervalsApiException(IntervalsErrorCode.INTERVALS_UNAVAILABLE,
                            "intervals.icu antwortet mit Fehler " + e.getStatusCode().value() + ".", null, e);
                }
                pause(path, attempt, backoff(attempt), String.valueOf(e.getStatusCode().value()));
            } catch (ResourceAccessException e) {
                if (attempt >= properties.maxRetries()) {
                    throw new IntervalsApiException(IntervalsErrorCode.INTERVALS_UNAVAILABLE,
                            "intervals.icu nicht erreichbar: " + e.getMessage(), null, e);
                }
                pause(path, attempt, backoff(attempt), e.getClass().getSimpleName());
            }
        }
    }

    private String execute(String path) {
        try {
            permits.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IntervalsApiException(IntervalsErrorCode.INTERVALS_UNAVAILABLE, "Abruf abgebrochen.", null, e);
        }
        try {
            String body = restClient.get()
                    .uri(properties.baseUrl() + path)
                    .header(HttpHeaders.AUTHORIZATION, basicAuth())
                    .retrieve()
                    .body(String.class);
            return body != null ? body : "";
        } finally {
            permits.release();
        }
    }

    private void pause(String path, int attempt, Duration wait, String reason) {
        log.warn("intervals.icu {} fuer {} - Versuch {} von {} in {} ms",
                reason, path, attempt + 2, properties.maxRetries() + 1, wait.toMillis());
        try {
            sleeper.sleep(wait);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IntervalsApiException(IntervalsErrorCode.INTERVALS_UNAVAILABLE, "Abruf abgebrochen.", null, e);
        }
    }

    private String basicAuth() {
        // intervals.icu: Benutzername "API_KEY", Passwort = API-Key
        String credentials = "API_KEY:" + properties.apiKey();
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private static Duration backoff(int attempt) {
        return BACKOFF[Math.min(attempt, BACKOFF.length - 1)];
    }

    private static Duration min(Duration a, Duration b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    /** Retry-After in Sekunden; null, wenn nicht gesetzt oder kein Sekundenwert. */
    static Duration retryAfter(RestClientResponseException e) {
        HttpHeaders headers = e.getResponseHeaders();
        String value = headers != null ? headers.getFirst(HttpHeaders.RETRY_AFTER) : null;
        if (value == null) return null;
        try {
            long seconds = Long.parseLong(value.trim());
            return seconds >= 0 ? Duration.ofSeconds(seconds) : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
