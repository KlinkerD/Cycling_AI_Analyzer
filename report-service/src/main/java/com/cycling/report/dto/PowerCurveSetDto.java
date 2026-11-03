package com.cycling.report.dto;

import java.util.List;

/**
 * Bestleistungs-Kurven fuer den Report:
 * - {@code current}: aktuelle Aktivitaet (deutlich darzustellen)
 * - {@code previous}: bis zu 3 zeitlich vorherige Aktivitaeten (transparent darzustellen,
 *   in Reihenfolge "neueste zuerst", damit das Frontend progressiv blasser einfaerben kann)
 * - {@code ftpWatts}: aktueller FTP fuer die Reflinie (kann null sein)
 */
public record PowerCurveSetDto(
        PowerCurveDto current,
        List<PowerCurveDto> previous,
        Integer ftpWatts
) {}

