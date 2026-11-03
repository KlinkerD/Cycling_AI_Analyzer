package com.cycling.messaging.contract;

/**
 * intervals-service -> alle (retained): die heutigen Fitness-Werte haben sich geaendert. Neue
 * Abonnenten bekommen sofort den letzten Stand. Gemeldet wird erst, wenn sich ein Wert nach Rundung
 * auf eine Nachkommastelle aendert.
 *
 * @param today       Werte von heute
 * @param previousDay Werte vom Vortag, zum Vergleich; null, wenn intervals.icu keine hat
 */
public record WellnessUpdated(Values today, Values previousDay) {

    /**
     * Werte eines Tages.
     *
     * @param date     yyyy-MM-dd
     * @param ctl      Fitness
     * @param atl      Fatigue
     * @param form     Form = CTL - ATL
     * @param rampRate Ramp laut intervals.icu
     */
    public record Values(String date, Double ctl, Double atl, Double form, Double rampRate) {
    }
}
