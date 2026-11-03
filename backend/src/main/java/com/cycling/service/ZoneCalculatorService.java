package com.cycling.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Liefert den Max-HR-Defaultwert, falls intervals.icu keine Schwellenwerte liefert.
 * Der FTP-Default ({@code cycling.ftp}) liegt in {@link FtpResolver}.
 * <p>
 * Die Zonen-Verteilung einer Aktivitaet wird NICHT mehr hier berechnet, sondern direkt aus den
 * von intervals.icu vorberechneten Zonen-Zeiten uebernommen
 * (siehe {@code IntervalsClient#getActivityZones}), damit die Report-Werte exakt mit der
 * intervals.icu-Website uebereinstimmen.
 */
@Service
public class ZoneCalculatorService {

    @Value("${cycling.max-hr:190}")
    private int defaultMaxHr;

    public int getMaxHr() { return defaultMaxHr; }
}
