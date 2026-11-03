package com.cycling.intervals.api;

/**
 * Ein Tag aus der Wellness-Reihe von intervals.icu.
 *
 * @param date     yyyy-MM-dd
 * @param ctl      Fitness (Chronic Training Load)
 * @param atl      Fatigue (Acute Training Load)
 * @param form     Form = CTL - ATL; null, wenn einer der beiden Werte fehlt
 * @param rampRate Ramp (CTL-Anstieg pro Woche) laut intervals.icu
 */
public record IntervalsWellnessPoint(String date, Double ctl, Double atl, Double form, Double rampRate) {
}
