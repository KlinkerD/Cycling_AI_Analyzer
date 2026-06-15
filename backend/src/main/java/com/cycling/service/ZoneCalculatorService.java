package com.cycling.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Liefert die FTP- und Max-HR-Defaultwerte, falls intervals.icu keine Schwellenwerte liefert.
 * <p>
 * Die Zonen-Verteilung einer Aktivitaet wird NICHT mehr hier berechnet, sondern direkt aus den
 * von intervals.icu vorberechneten Zonen-Zeiten uebernommen
 * (siehe {@code IntervalsService#getActivityZones}), damit die Report-Werte exakt mit der
 * intervals.icu-Website uebereinstimmen.
 */
@Service
public class ZoneCalculatorService {

    @Value("${cycling.ftp:250}")
    private int defaultFtp;

    @Value("${cycling.max-hr:190}")
    private int defaultMaxHr;

    public int getFtp() { return defaultFtp; }
    public int getMaxHr() { return defaultMaxHr; }
}
