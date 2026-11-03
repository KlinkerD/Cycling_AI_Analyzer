package com.cycling.intervals.api;

/**
 * Eine Trainingszone (Power oder HF).
 *
 * @param index      1-basierte Zonen-Nummer (Sweet Spot = 8)
 * @param lowerPct   untere Grenze als Anteil der Referenz (FTP bzw. LTHR/MaxHR)
 * @param upperPct   obere Grenze als Anteil; null = nach oben offen
 * @param lowerValue untere Grenze in Watt bzw. bpm
 * @param upperValue obere Grenze in Watt bzw. bpm; null = nach oben offen
 * @param color      Hex-Farbe
 */
public record IntervalsZone(int index, String name, Double lowerPct, Double upperPct, Integer lowerValue,
                            Integer upperValue, String color) {
}
