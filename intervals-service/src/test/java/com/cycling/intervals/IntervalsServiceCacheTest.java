package com.cycling.intervals;

import com.cycling.intervals.api.IntervalsActivityZones;
import com.cycling.intervals.api.IntervalsAthleteThresholds;
import com.cycling.intervals.api.IntervalsAthleteZones;
import com.cycling.intervals.api.IntervalsErrorCode;
import com.cycling.intervals.api.IntervalsStreams;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntervalsServiceCacheTest {

    private static final IntervalsAthleteZones ZONES = new IntervalsAthleteZones(
            new IntervalsAthleteThresholds("Dan", 250, null, 170, 190, null), List.of(), List.of(), "lthr");

    private final IntervalsGateway gateway = mock(IntervalsGateway.class);
    private final IntervalsService service = new IntervalsService(gateway);

    @Test
    void secondRequestIsServedFromCache() {
        IntervalsStreams streams = new IntervalsStreams(List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of());
        when(gateway.streams("i1")).thenReturn(streams);

        assertThat(service.streams("i1")).isSameAs(streams);
        assertThat(service.streams("i1")).isSameAs(streams);

        verify(gateway, times(1)).streams("i1");
    }

    @Test
    void thresholdsAndActivityZonesShareTheAthleteCall() {
        when(gateway.athleteZones()).thenReturn(ZONES);
        when(gateway.activityZones(any(), any()))
                .thenReturn(new IntervalsActivityZones(List.of(), List.of(), false, null));

        service.thresholds();
        service.athleteZones();
        service.activityZones("i1");
        service.activityZones("i1");

        verify(gateway, times(1)).athleteZones();
        verify(gateway, times(1)).activityZones("i1", ZONES);
    }

    @Test
    void evictForcesNewRequest() {
        LocalDate from = LocalDate.of(2026, 11, 1);
        LocalDate to = LocalDate.of(2026, 11, 3);
        when(gateway.wellness(from, to)).thenReturn(List.of());

        service.wellness(from, to);
        service.evictAll();
        service.wellness(from, to);

        verify(gateway, times(2)).wellness(from, to);
    }

    @Test
    void errorsAreNotCached() {
        when(gateway.athleteZones())
                .thenThrow(new IntervalsApiException(IntervalsErrorCode.INTERVALS_UNAVAILABLE, "weg"))
                .thenReturn(ZONES);

        assertThatThrownBy(service::athleteZones).isInstanceOf(IntervalsApiException.class);
        assertThat(service.athleteZones()).isEqualTo(ZONES);
    }
}
