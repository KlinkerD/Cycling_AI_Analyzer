package com.cycling.stats.api;

import com.cycling.web.ApiNonNull;

import java.time.LocalDate;
import java.util.List;

/**
 * Summen ueber einen Zeitraum. Fahrten ohne Leistungs- oder HF-Daten zaehlen bei Strecke und Zeit mit,
 * fehlen aber in den Zonen ({@code ridesWithPower} / {@code ridesWithHeartRate} zeigen, wie viele beitragen).
 *
 * @param from               erster Tag (inklusive), auf die Abfrage zugeschnitten
 * @param to                 letzter Tag (inklusive), auf die Abfrage zugeschnitten
 * @param rides              Anzahl Fahrten
 * @param distance           Meter
 * @param movingTime         Bewegungszeit in Sekunden
 * @param elapsedTime        Gesamtzeit in Sekunden
 * @param elevationGain      Hoehenmeter
 * @param trainingLoad       Summe der Trainingslast (TSS laut intervals.icu)
 * @param ridesWithPower     Fahrten mit Zeit in Leistungszonen
 * @param ridesWithHeartRate Fahrten mit Zeit in HF-Zonen
 * @param powerZones         Zeit je Leistungszone (Z1-Z7), aufsteigend; leer ohne Leistungsdaten
 * @param hrZones            Zeit je HF-Zone, aufsteigend; leer ohne HF-Daten
 */
public record StatsPeriodDto(
        @ApiNonNull LocalDate from,
        @ApiNonNull LocalDate to,
        int rides,
        double distance,
        long movingTime,
        long elapsedTime,
        double elevationGain,
        double trainingLoad,
        int ridesWithPower,
        int ridesWithHeartRate,
        @ApiNonNull List<ZoneTimeDto> powerZones,
        @ApiNonNull List<ZoneTimeDto> hrZones
) {
}
