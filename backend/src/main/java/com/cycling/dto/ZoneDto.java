package com.cycling.dto;

/**
 * Eine einzelne Trainingszone (Power oder HF).
 *
 * @param index      1-basierte Zonen-Nummer
 * @param name       Anzeigename (z.B. "Z2 - Endurance")
 * @param lowerPct   Untere Grenze als Anteil der Referenz (0.0 - n)
 * @param upperPct   Obere Grenze als Anteil; null = open ended
 * @param lowerValue Untere Grenze in Watt bzw. bpm
 * @param upperValue Obere Grenze in Watt bzw. bpm; null = open ended
 * @param color      Hex-Farbe fuer Stacked-Bar Visualisierung
 */
public record ZoneDto(
        int index,
        String name,
        Double lowerPct,
        Double upperPct,
        Integer lowerValue,
        Integer upperValue,
        String color
) {}

