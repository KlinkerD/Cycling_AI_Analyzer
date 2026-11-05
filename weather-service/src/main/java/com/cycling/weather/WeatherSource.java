package com.cycling.weather;

import java.time.LocalDate;
import java.util.List;

/** Liefert Modellreihen fuer mehrere Orte und einen Zeitraum (UTC-Tage, beide inklusive). */
interface WeatherSource {

    /**
     * @return eine Reihe pro Ort, gleiche Reihenfolge wie {@code locations}
     * @throws WeatherSourceException wenn die Quelle nicht oder unlesbar antwortet
     */
    List<Series> fetch(List<Location> locations, LocalDate fromUtc, LocalDate toUtc);
}
