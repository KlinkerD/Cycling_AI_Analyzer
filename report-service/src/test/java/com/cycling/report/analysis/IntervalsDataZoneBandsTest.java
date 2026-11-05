package com.cycling.report.analysis;

import com.cycling.intervals.api.IntervalsAthleteThresholds;
import com.cycling.intervals.api.IntervalsAthleteZones;
import com.cycling.intervals.api.IntervalsZone;
import com.cycling.intervals.client.IntervalsClient;
import com.cycling.report.api.ZoneBandDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Zonengrenzen fuer die Karte: Leistung mit der FTP der Fahrt, HF mit den aktuellen Grenzen. */
class IntervalsDataZoneBandsTest {

    private final IntervalsClient client = mock(IntervalsClient.class);
    private final IntervalsData data = new IntervalsData(client);

    private void zones(List<IntervalsZone> power, List<IntervalsZone> hr) {
        when(client.athleteZonesOrEmpty()).thenReturn(new IntervalsAthleteZones(
                new IntervalsAthleteThresholds("A", 300, null, 170, 190, null), power, hr, "lthr"));
    }

    /** Aktuelle FTP 300 W. */
    private static final List<IntervalsZone> POWER = List.of(
            new IntervalsZone(1, "Recovery", 0.0, 0.55, 0, 165, "#9e9e9e"),
            new IntervalsZone(2, "Endurance", 0.55, 0.75, 165, 225, "#42a5f5"),
            new IntervalsZone(7, "Neuromuscular", 1.5, null, 450, null, "#ab47bc"),
            new IntervalsZone(8, "Sweet Spot", 0.84, 0.97, 252, 291, "#66bb6a"));

    @Test
    void powerBandsUseFtpOfRideAndSkipSweetSpot() {
        zones(POWER, List.of());

        List<ZoneBandDto> bands = data.getZoneBands(200).power();

        assertThat(bands).containsExactly(
                new ZoneBandDto("Recovery", 0, 110, "#9e9e9e"),
                new ZoneBandDto("Endurance", 110, 150, "#42a5f5"),
                new ZoneBandDto("Neuromuscular", 300, null, "#ab47bc"));
    }

    @Test
    void withoutFtpTheCurrentWattLimitsAreUsed() {
        zones(POWER, List.of());

        assertThat(data.getZoneBands(null).power()).extracting(ZoneBandDto::lower).containsExactly(0, 165, 450);
    }

    @Test
    void hrBandsKeepCurrentBpmLimits() {
        zones(List.of(), List.of(
                new IntervalsZone(1, "Z1", 0.0, 0.8, 0, 136, "#9e9e9e"),
                new IntervalsZone(5, "Z5", 1.0, null, 170, null, "#ef5350")));

        assertThat(data.getZoneBands(200).hr()).containsExactly(
                new ZoneBandDto("Z1", 0, 136, "#9e9e9e"),
                new ZoneBandDto("Z5", 170, null, "#ef5350"));
    }
}
