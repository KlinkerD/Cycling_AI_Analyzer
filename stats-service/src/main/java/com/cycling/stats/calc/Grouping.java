package com.cycling.stats.calc;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/** Aufteilung einer Statistik in Kalender-Zeitraeume. */
public enum Grouping {
    /** Nur die Summe, keine Aufteilung. */
    NONE,
    /** ISO-Woche, Montag bis Sonntag. */
    WEEK,
    /** Kalendermonat. */
    MONTH,
    /** Kalenderjahr, 1.1. bis 31.12. */
    YEAR;

    /** Erster Tag des Zeitraums, in dem {@code date} liegt (ohne Zuschnitt). */
    public LocalDate periodStart(LocalDate date) {
        return switch (this) {
            case NONE -> LocalDate.MIN;
            case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> date.withDayOfMonth(1);
            case YEAR -> date.withDayOfYear(1);
        };
    }

    /** Letzter Tag des Zeitraums, der am {@code start} beginnt (ohne Zuschnitt). */
    public LocalDate periodEnd(LocalDate start) {
        return switch (this) {
            case NONE -> LocalDate.MAX;
            case WEEK -> start.plusDays(6);
            case MONTH -> start.plusMonths(1).minusDays(1);
            case YEAR -> start.plusYears(1).minusDays(1);
        };
    }
}
