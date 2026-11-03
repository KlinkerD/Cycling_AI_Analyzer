package com.cycling.recommendation;

import com.cycling.intervals.api.IntervalsActivity;
import com.cycling.intervals.api.IntervalsWellnessPoint;

import java.time.LocalDate;
import java.util.List;

/** Daten vom intervals-service, die die Empfehlung braucht. */
public interface IntervalsData {

    /**
     * Wellness pro Tag, aufsteigend.
     *
     * @throws IntervalsUnavailableException wenn der intervals-service nicht liefert
     */
    List<IntervalsWellnessPoint> wellness(LocalDate from, LocalDate to);

    /**
     * Rad-Aktivitaeten im Zeitraum.
     *
     * @throws IntervalsUnavailableException wenn der intervals-service nicht liefert
     */
    List<IntervalsActivity> activities(LocalDate from, LocalDate to);
}
