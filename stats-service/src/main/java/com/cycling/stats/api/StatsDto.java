package com.cycling.stats.api;

import com.cycling.stats.calc.Grouping;
import com.cycling.web.ApiNonNull;

import java.time.LocalDate;
import java.util.List;

/**
 * Statistik ueber einen Zeitraum.
 *
 * @param total   Summe ueber den ganzen Zeitraum
 * @param periods Aufteilung nach {@code groupBy}, aufsteigend, auch Zeitraeume ohne Fahrten; leer bei NONE
 */
public record StatsDto(
        @ApiNonNull LocalDate from,
        @ApiNonNull LocalDate to,
        @ApiNonNull Grouping groupBy,
        @ApiNonNull StatsPeriodDto total,
        @ApiNonNull List<StatsPeriodDto> periods
) {
}
