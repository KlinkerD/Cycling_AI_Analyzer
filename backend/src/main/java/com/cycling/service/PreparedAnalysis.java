package com.cycling.service;

import com.cycling.dto.StreamDataDto;
import com.cycling.dto.ZoneDistributionDto;
import com.cycling.model.ActivityAnalysis;

import java.util.List;

/** Alles, was vor der KI-Bewertung feststeht: Analyse ohne KI-Felder plus Kontext fuer Prompt und Report. */
public record PreparedAnalysis(
        ActivityAnalysis analysis,
        StreamDataDto streams,
        List<ZoneDistributionDto> powerZones,
        List<ZoneDistributionDto> hrZones,
        List<ActivityAnalysis> lastThree,
        int ftp,
        int maxHr
) {
}
