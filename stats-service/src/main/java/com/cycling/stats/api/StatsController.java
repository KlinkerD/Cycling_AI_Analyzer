package com.cycling.stats.api;

import com.cycling.stats.calc.Grouping;
import com.cycling.stats.calc.StatsCalculator;
import com.cycling.stats.store.RideRepository;
import com.cycling.stats.sync.RideSync;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;

/**
 * REST-API fuer das Frontend: Statistik ueber die gespeicherten Fahrten. Liest nur aus der eigenen
 * Datenbank, ruft den intervals-service also nie direkt auf.
 */
@RestController
@Tag(name = "Stats")
@RequestMapping("/api/stats")
public class StatsController {

    /** Laengster abfragbarer Zeitraum, damit WEEK keine riesigen Antworten erzeugt. */
    static final int MAX_DAYS = 3660;

    private final RideRepository repository;
    private final RideSync sync;
    private final Clock clock;

    public StatsController(RideRepository repository, RideSync sync, Clock clock) {
        this.repository = repository;
        this.sync = sync;
        this.clock = clock;
    }

    /**
     * GET /api/stats?from&amp;to&amp;groupBy - Summen von {@code from} bis {@code to} (beide inklusive),
     * aufgeteilt nach Kalenderwoche, -monat oder -jahr. 400, wenn from nach to liegt oder der Zeitraum
     * laenger als 10 Jahre ist.
     */
    @GetMapping
    public ResponseEntity<StatsDto> getStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "MONTH") Grouping groupBy) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from (" + from + ") liegt nach to (" + to + ")");
        }
        if (from.plusDays(MAX_DAYS).isBefore(to)) {
            throw new IllegalArgumentException("Zeitraum zu lang, hoechstens " + MAX_DAYS + " Tage");
        }
        return ResponseEntity.ok(stats(from, to, groupBy));
    }

    /**
     * GET /api/stats/year-to-date?groupBy - laufendes Jahr vom 1.1. bis heute und derselbe Zeitraum im
     * Vorjahr.
     */
    @GetMapping("/year-to-date")
    public ResponseEntity<YearToDateDto> getYearToDate(
            @RequestParam(required = false, defaultValue = "MONTH") Grouping groupBy) {
        LocalDate today = LocalDate.now(clock);
        LocalDate lastYearToday = today.minusYears(1);
        return ResponseEntity.ok(new YearToDateDto(today.getYear(),
                stats(today.withDayOfYear(1), today, groupBy),
                stats(lastYearToday.withDayOfYear(1), lastYearToday, groupBy),
                sync.importRunning()));
    }

    /** GET /api/stats/status - wie viele Fahrten gespeichert sind und ob der Erstimport noch laeuft. */
    @GetMapping("/status")
    public ResponseEntity<SyncStatusDto> getStatus() {
        return ResponseEntity.ok(new SyncStatusDto(repository.count(), repository.countByZonesLoadedFalse(),
                repository.oldestDate(), repository.newestDate(), repository.lastSyncedAt(), sync.importStart(),
                sync.importRunning()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage()));
    }

    private StatsDto stats(LocalDate from, LocalDate to, Grouping groupBy) {
        return StatsCalculator.calculate(repository.findByDateBetween(from, to), from, to, groupBy);
    }
}
