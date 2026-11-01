package com.cycling.service;

import com.cycling.model.ActivityAnalysis;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisServiceFtpTest {

    /** Alte Indoor-Analyse: NP 200 W, 1 h, damals mit Outdoor-FTP 250 W gerechnet (IF 0.80, TSS 64). */
    private static ActivityAnalysis oldIndoorAnalysis() {
        ActivityAnalysis a = new ActivityAnalysis();
        a.setActivityId("i1");
        a.setNormalizedPowerWatts(200.0);
        a.setDurationSeconds(3600);
        a.setIntensityFactor(0.80);
        a.setTrainingLoad(64.0);
        return a;
    }

    @Test
    void applyFtpContextStoresIndoorFlagAndFtp() {
        ActivityAnalysis a = oldIndoorAnalysis();

        AnalysisService.applyFtpContext(a, true, 235);

        assertThat(a.getIndoor()).isTrue();
        assertThat(a.getFtpUsed()).isEqualTo(235);
    }

    @Test
    void applyFtpContextRecomputesIntensityAndTssFromStoredNp() {
        ActivityAnalysis a = oldIndoorAnalysis();

        AnalysisService.applyFtpContext(a, true, 235);

        // IF = 200 / 235 = 0.851 -> 0.85 ; TSS = 1 h * IF^2 * 100 = 72.4 -> 72
        assertThat(a.getIntensityFactor()).isEqualTo(0.85);
        assertThat(a.getTrainingLoad()).isEqualTo(72.0);
    }

    @Test
    void applyFtpContextWithoutNpLeavesLoadMetricsUntouched() {
        ActivityAnalysis a = new ActivityAnalysis();
        a.setDurationSeconds(3600);

        AnalysisService.applyFtpContext(a, false, 250);

        assertThat(a.getFtpUsed()).isEqualTo(250);
        assertThat(a.getIndoor()).isFalse();
        assertThat(a.getIntensityFactor()).isNull();
        assertThat(a.getTrainingLoad()).isNull();
    }
}
