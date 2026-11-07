package com.cycling.intervals;

import com.cycling.intervals.api.IntervalsAthleteZones;
import com.cycling.intervals.api.IntervalsErrorCode;
import com.cycling.intervals.api.IntervalsThresholdsUpdate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Welche PUT-Aufrufe das Gateway fuer ein Update an intervals.icu schickt. */
class IntervalsGatewayUpdateTest {

    private static final String ATHLETE = """
            {"name":"Dan","icu_weight":72.5,"sportSettings":[
              {"id":11,"types":["Run"],"ftp":0,"lthr":175},
              {"id":42,"types":["Ride","VirtualRide"],"ftp":250,"indoor_ftp":235,"lthr":170,"max_hr":190,
               "power_zones":[55,75,90,105,120,150,999],"hr_zones":[140,155,162,173,178,183,190]}
            ]}""";

    private final IntervalsHttpClient http = mock(IntervalsHttpClient.class);
    private final IntervalsGateway gateway = new IntervalsGateway(http);

    @Test
    void writesOnlyGivenFieldsToTheRideSetting() {
        when(http.athleteId()).thenReturn("i1");
        when(http.get("/athlete/i1")).thenReturn(ATHLETE);

        IntervalsAthleteZones fresh = gateway.updateThresholds(IntervalsThresholdsUpdate.thresholds(280, null, null, null, null));

        verify(http).put("/athlete/i1/sport-settings/42?recalcHrZones=false", "{\"ftp\":280}");
        verify(http, never()).put(org.mockito.ArgumentMatchers.eq("/athlete/i1"), anyString());
        assertThat(fresh.thresholds().name()).isEqualTo("Dan");
    }

    @Test
    void heartRateChangeRecalculatesHrZones() {
        when(http.athleteId()).thenReturn("i1");
        when(http.get("/athlete/i1")).thenReturn(ATHLETE);

        gateway.updateThresholds(IntervalsThresholdsUpdate.thresholds(null, 260, 168, 186, null));

        verify(http).put("/athlete/i1/sport-settings/42?recalcHrZones=true",
                "{\"indoor_ftp\":260,\"lthr\":168,\"max_hr\":186}");
    }

    @Test
    void weightGoesToTheAthleteWithoutTouchingSportSettings() {
        when(http.athleteId()).thenReturn("i1");
        when(http.get("/athlete/i1")).thenReturn(ATHLETE);

        gateway.updateThresholds(IntervalsThresholdsUpdate.thresholds(null, null, null, null, 74.0));

        verify(http).put("/athlete/i1", "{\"icu_weight\":74.0}");
        verify(http, never()).put(org.mockito.ArgumentMatchers.startsWith("/athlete/i1/sport-settings"), anyString());
    }

    private static IntervalsThresholdsUpdate zones(Integer lthr, Integer maxHr, List<Integer> power, List<Integer> hr,
                                                   Integer ssMin, Integer ssMax) {
        return new IntervalsThresholdsUpdate(null, null, lthr, maxHr, null, power, hr, ssMin, ssMax);
    }

    @Test
    void powerZonesKeepTheOpenTopLimit() {
        when(http.athleteId()).thenReturn("i1");
        when(http.get("/athlete/i1")).thenReturn(ATHLETE);

        gateway.updateThresholds(zones(null, null, List.of(56, 76, 91, 106, 121, 151), null, 85, 95));

        verify(http).put("/athlete/i1/sport-settings/42?recalcHrZones=false",
                "{\"power_zones\":[56,76,91,106,121,151,999],\"sweet_spot_min\":85,\"sweet_spot_max\":95}");
    }

    @Test
    void hrZonesAreConvertedToBpmFromNewLthrWithoutIntervalsRecalculating() {
        when(http.athleteId()).thenReturn("i1");
        when(http.get("/athlete/i1")).thenReturn(ATHLETE);

        // LTHR 172: 81 % = 139,32 -> 139; 89 % = 153,08 -> 153; 103 % = 177,16 -> 177; letzte Grenze = neue HFmax
        gateway.updateThresholds(zones(172, 192, null, List.of(81, 89, 94, 99, 103, 106), null, null));

        verify(http).put("/athlete/i1/sport-settings/42?recalcHrZones=false",
                "{\"lthr\":172,\"max_hr\":192,\"hr_zones\":[139,153,162,170,177,182,192]}");
    }

    @Test
    void hrZonesUseTheStoredLthrAndLastLimitWhenNotGiven() {
        when(http.athleteId()).thenReturn("i1");
        when(http.get("/athlete/i1")).thenReturn(ATHLETE);

        // LTHR 170: 82 % = 139,4 -> 139; kaufmaennisch: 85 % = 144,5 -> 145; letzte Grenze bleibt 190
        gateway.updateThresholds(zones(null, null, null, List.of(82, 85, 94, 99, 102, 106), null, null));

        verify(http).put("/athlete/i1/sport-settings/42?recalcHrZones=false",
                "{\"hr_zones\":[139,145,160,168,173,180,190]}");
    }

    @Test
    void wrongZoneCountIsBadRequest() {
        when(http.athleteId()).thenReturn("i1");
        when(http.get("/athlete/i1")).thenReturn(ATHLETE);

        assertThatThrownBy(() -> gateway.updateThresholds(zones(null, null, List.of(55, 75, 90), null, null, null)))
                .isInstanceOfSatisfying(IntervalsApiException.class, e -> {
                    assertThat(e.code()).isEqualTo(IntervalsErrorCode.BAD_REQUEST);
                    assertThat(e.getMessage()).contains("intervals.icu hat 7 Zonen, gesendet wurden 4");
                });
        verify(http, never()).put(anyString(), anyString());
    }

    @Test
    void hrLimitAboveMaxHrPushesTheLastLimitUp() {
        when(http.athleteId()).thenReturn("i1");
        when(http.get("/athlete/i1")).thenReturn(ATHLETE);

        // 115 % von 170 = 196 bpm liegt ueber HFmax 190: letzte Grenze wird 197, kein Fehler
        gateway.updateThresholds(zones(null, null, null, List.of(82, 89, 94, 99, 102, 115), null, null));

        verify(http).put("/athlete/i1/sport-settings/42?recalcHrZones=false",
                "{\"hr_zones\":[139,151,160,168,173,196,197]}");
    }

    @Test
    void missingRideSettingIsNotFound() {
        when(http.athleteId()).thenReturn("i1");
        when(http.get("/athlete/i1")).thenReturn("{\"sportSettings\":[{\"id\":11,\"types\":[\"Run\"]}]}");

        assertThatThrownBy(() -> gateway.updateThresholds(IntervalsThresholdsUpdate.thresholds(280, null, null, null, null)))
                .isInstanceOfSatisfying(IntervalsApiException.class,
                        e -> assertThat(e.code()).isEqualTo(IntervalsErrorCode.NOT_FOUND));
        verify(http, never()).put(anyString(), anyString());
    }
}
