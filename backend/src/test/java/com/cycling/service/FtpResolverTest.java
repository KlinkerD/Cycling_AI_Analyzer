package com.cycling.service;

import com.cycling.dto.AthleteThresholdsDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FtpResolverTest {

    @Test
    void virtualRideIsIndoor() {
        assertThat(FtpResolver.isIndoor("VirtualRide", null)).isTrue();
    }

    @Test
    void rideOnTrainerIsIndoor() {
        assertThat(FtpResolver.isIndoor("Ride", true)).isTrue();
    }

    @Test
    void virtualRideWithoutTrainerFlagIsStillIndoor() {
        assertThat(FtpResolver.isIndoor("VirtualRide", false)).isTrue();
    }

    @Test
    void rideWithoutTrainerIsOutdoor() {
        assertThat(FtpResolver.isIndoor("Ride", false)).isFalse();
        assertThat(FtpResolver.isIndoor("Ride", null)).isFalse();
    }

    @Test
    void unknownTypeIsOutdoor() {
        assertThat(FtpResolver.isIndoor(null, null)).isFalse();
    }

    // ---- resolve: icu_ftp -> Indoor-/Outdoor-FTP -> Default ----

    private static final int DEFAULT_FTP = 200;
    private final FtpResolver resolver = new FtpResolver(DEFAULT_FTP);

    private static AthleteThresholdsDto thresholds(Integer ftp, Integer indoorFtp) {
        return new AthleteThresholdsDto("Test", ftp, indoorFtp, null, null, null);
    }

    @Test
    void icuFtpOfActivityWins() {
        assertThat(resolver.resolve(240, true, thresholds(250, 235))).isEqualTo(240);
        assertThat(resolver.resolve(240, false, thresholds(250, 235))).isEqualTo(240);
    }

    @Test
    void indoorRideWithoutIcuFtpUsesIndoorFtp() {
        assertThat(resolver.resolve(null, true, thresholds(250, 235))).isEqualTo(235);
    }

    @Test
    void indoorRideWithoutIndoorFtpFallsBackToOutdoorFtp() {
        assertThat(resolver.resolve(null, true, thresholds(250, null))).isEqualTo(250);
    }

    @Test
    void outdoorRideIgnoresIndoorFtp() {
        assertThat(resolver.resolve(null, false, thresholds(250, 235))).isEqualTo(250);
    }

    @Test
    void withoutAnyFtpUsesConfiguredDefault() {
        assertThat(resolver.resolve(null, true, thresholds(null, null))).isEqualTo(DEFAULT_FTP);
        assertThat(resolver.resolve(null, false, null)).isEqualTo(DEFAULT_FTP);
    }

    @Test
    void zeroValuesCountAsMissing() {
        assertThat(resolver.resolve(0, true, thresholds(250, 0))).isEqualTo(250);
    }
}
