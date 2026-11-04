package com.cycling.intervals;

import com.cycling.intervals.api.IntervalsAthleteZones;
import com.cycling.intervals.api.IntervalsErrorCode;
import com.cycling.intervals.api.IntervalsThresholdsUpdate;
import org.junit.jupiter.api.Test;

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
              {"id":42,"types":["Ride","VirtualRide"],"ftp":250,"indoor_ftp":235,"lthr":170,"max_hr":190}
            ]}""";

    private final IntervalsHttpClient http = mock(IntervalsHttpClient.class);
    private final IntervalsGateway gateway = new IntervalsGateway(http);

    @Test
    void writesOnlyGivenFieldsToTheRideSetting() {
        when(http.athleteId()).thenReturn("i1");
        when(http.get("/athlete/i1")).thenReturn(ATHLETE);

        IntervalsAthleteZones fresh = gateway.updateThresholds(new IntervalsThresholdsUpdate(280, null, null, null, null));

        verify(http).put("/athlete/i1/sport-settings/42?recalcHrZones=false", "{\"ftp\":280}");
        verify(http, never()).put(org.mockito.ArgumentMatchers.eq("/athlete/i1"), anyString());
        assertThat(fresh.thresholds().name()).isEqualTo("Dan");
    }

    @Test
    void heartRateChangeRecalculatesHrZones() {
        when(http.athleteId()).thenReturn("i1");
        when(http.get("/athlete/i1")).thenReturn(ATHLETE);

        gateway.updateThresholds(new IntervalsThresholdsUpdate(null, 260, 168, 186, null));

        verify(http).put("/athlete/i1/sport-settings/42?recalcHrZones=true",
                "{\"indoor_ftp\":260,\"lthr\":168,\"max_hr\":186}");
    }

    @Test
    void weightGoesToTheAthleteWithoutTouchingSportSettings() {
        when(http.athleteId()).thenReturn("i1");
        when(http.get("/athlete/i1")).thenReturn(ATHLETE);

        gateway.updateThresholds(new IntervalsThresholdsUpdate(null, null, null, null, 74.0));

        verify(http).put("/athlete/i1", "{\"icu_weight\":74.0}");
        verify(http, never()).put(org.mockito.ArgumentMatchers.startsWith("/athlete/i1/sport-settings"), anyString());
    }

    @Test
    void missingRideSettingIsNotFound() {
        when(http.athleteId()).thenReturn("i1");
        when(http.get("/athlete/i1")).thenReturn("{\"sportSettings\":[{\"id\":11,\"types\":[\"Run\"]}]}");

        assertThatThrownBy(() -> gateway.updateThresholds(new IntervalsThresholdsUpdate(280, null, null, null, null)))
                .isInstanceOfSatisfying(IntervalsApiException.class,
                        e -> assertThat(e.code()).isEqualTo(IntervalsErrorCode.NOT_FOUND));
        verify(http, never()).put(anyString(), anyString());
    }
}
