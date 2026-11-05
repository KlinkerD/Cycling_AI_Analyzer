package com.cycling.report.analysis;

import com.cycling.types.ActivityId;
import com.cycling.report.api.StreamDataDto;
import com.cycling.report.api.ZoneDistributionDto;
import com.cycling.report.weather.RideWeatherPoint;
import com.cycling.report.weather.WeatherSummary;

import java.util.List;
import java.util.Map;

/**
 * Alles, was vor der KI-Bewertung feststeht: Analyse ohne KI-Felder plus Kontext fuer Prompt und Report.
 *
 * @param weather         Wetter alle 10 Minuten; leer bei Indoor-Fahrten, ohne GPS oder wenn der weather-service
 *                        nicht liefert
 * @param previousWeather Wetter-Kennzahlen der Vergleichsfahrten nach Aktivitaets-ID; fehlt ohne Wetter
 */
public record PreparedAnalysis(
        ActivityAnalysis analysis,
        StreamDataDto streams,
        List<ZoneDistributionDto> powerZones,
        List<ZoneDistributionDto> hrZones,
        List<ActivityAnalysis> lastThree,
        int ftp,
        int maxHr,
        List<RideWeatherPoint> weather,
        Map<ActivityId, WeatherSummary> previousWeather
) {

    public PreparedAnalysis {
        weather = weather == null ? List.of() : List.copyOf(weather);
        previousWeather = previousWeather == null ? Map.of() : Map.copyOf(previousWeather);
    }
}
