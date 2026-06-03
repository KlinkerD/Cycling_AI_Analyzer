package com.cycling.service;

import com.cycling.dto.ZoneDistributionDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ZoneCalculatorService {

    @Value("${cycling.ftp:250}")
    private int defaultFtp;

    @Value("${cycling.max-hr:190}")
    private int defaultMaxHr;

    // Power Zones nach FTP (% FTP)
    private static final double[][] POWER_ZONE_BOUNDS = {
            {0, 0.55},   // Z1: Active Recovery
            {0.55, 0.75}, // Z2: Endurance
            {0.75, 0.90}, // Z3: Tempo
            {0.90, 1.05}, // Z4: Threshold
            {1.05, 1.20}, // Z5: VO2 Max
            {1.20, 1.50}, // Z6: Anaerobic
            {1.50, 999}   // Z7: Neuromuscular
    };

    private static final String[] POWER_ZONE_NAMES = {
            "Z1", "Z2", "Z3", "Z4", "Z5", "Z6", "Z7"
    };

    private static final String[] POWER_ZONE_LABELS = {
            "Active Recovery", "Endurance", "Tempo", "Threshold", "VO2 Max", "Anaerobic", "Neuromuscular"
    };

    private static final String[] POWER_ZONE_COLORS = {
            "#9E9E9E", "#4CAF50", "#2196F3", "#FF9800", "#F44336", "#9C27B0", "#212121"
    };

    // HR Zones nach % Max HR
    private static final double[][] HR_ZONE_BOUNDS = {
            {0, 0.60},   // Z1: Recovery
            {0.60, 0.70}, // Z2: Endurance
            {0.70, 0.80}, // Z3: Aerobic
            {0.80, 0.90}, // Z4: Threshold
            {0.90, 999}   // Z5: VO2 Max
    };

    private static final String[] HR_ZONE_NAMES = {
            "Z1", "Z2", "Z3", "Z4", "Z5"
    };

    private static final String[] HR_ZONE_LABELS = {
            "Recovery", "Endurance", "Aerobic", "Threshold", "VO2 Max"
    };

    private static final String[] HR_ZONE_COLORS = {
            "#9E9E9E", "#4CAF50", "#2196F3", "#FF9800", "#F44336"
    };

    public List<ZoneDistributionDto> calculatePowerZones(List<Integer> watts) {
        return calculatePowerZones(watts, defaultFtp);
    }

    public List<ZoneDistributionDto> calculatePowerZones(List<Integer> watts, int ftp) {
        if (watts == null || watts.isEmpty()) return List.of();

        int[] zoneCounts = new int[POWER_ZONE_BOUNDS.length];
        int totalValid = 0;

        for (Integer w : watts) {
            if (w == null || w <= 0) continue;
            double ratio = (double) w / ftp;
            for (int i = 0; i < POWER_ZONE_BOUNDS.length; i++) {
                if (ratio >= POWER_ZONE_BOUNDS[i][0] && ratio < POWER_ZONE_BOUNDS[i][1]) {
                    zoneCounts[i]++;
                    totalValid++;
                    break;
                }
            }
        }

        List<ZoneDistributionDto> result = new ArrayList<>();
        for (int i = 0; i < POWER_ZONE_BOUNDS.length; i++) {
            double pct = totalValid > 0 ? (double) zoneCounts[i] / totalValid * 100 : 0;
            result.add(new ZoneDistributionDto(
                    POWER_ZONE_NAMES[i],
                    POWER_ZONE_LABELS[i],
                    zoneCounts[i],
                    Math.round(pct * 10.0) / 10.0,
                    POWER_ZONE_COLORS[i]
            ));
        }
        return result;
    }

    public List<ZoneDistributionDto> calculateHrZones(List<Integer> heartrates) {
        return calculateHrZones(heartrates, defaultMaxHr);
    }

    public List<ZoneDistributionDto> calculateHrZones(List<Integer> heartrates, int maxHr) {
        if (heartrates == null || heartrates.isEmpty()) return List.of();

        int[] zoneCounts = new int[HR_ZONE_BOUNDS.length];
        int totalValid = 0;

        for (Integer hr : heartrates) {
            if (hr == null || hr <= 0) continue;
            double ratio = (double) hr / maxHr;
            for (int i = 0; i < HR_ZONE_BOUNDS.length; i++) {
                if (ratio >= HR_ZONE_BOUNDS[i][0] && ratio < HR_ZONE_BOUNDS[i][1]) {
                    zoneCounts[i]++;
                    totalValid++;
                    break;
                }
            }
        }

        List<ZoneDistributionDto> result = new ArrayList<>();
        for (int i = 0; i < HR_ZONE_BOUNDS.length; i++) {
            double pct = totalValid > 0 ? (double) zoneCounts[i] / totalValid * 100 : 0;
            result.add(new ZoneDistributionDto(
                    HR_ZONE_NAMES[i],
                    HR_ZONE_LABELS[i],
                    zoneCounts[i],
                    Math.round(pct * 10.0) / 10.0,
                    HR_ZONE_COLORS[i]
            ));
        }
        return result;
    }

    public int getFtp() { return defaultFtp; }
    public int getMaxHr() { return defaultMaxHr; }
}

