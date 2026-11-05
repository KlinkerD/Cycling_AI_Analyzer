package com.cycling.ftp;

import com.cycling.intervals.api.IntervalsStreams;
import com.cycling.intervals.client.IntervalsClient;
import com.cycling.intervals.client.IntervalsUnavailableException;
import com.cycling.types.ActivityId;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IntervalsStreamsClientTest {

    private static final ActivityId I1 = ActivityId.of("i1");

    private final IntervalsClient intervals = mock(IntervalsClient.class);
    private final IntervalsStreamsClient client = new IntervalsStreamsClient(intervals);

    @Test
    void keepsOnlyTimePowerAndHeartRate() {
        when(intervals.streams(I1)).thenReturn(new IntervalsStreams(List.of(0.0, 5.0), List.of(200, 210),
                List.of(140, 141), List.of(), List.of(), List.of(0, 1), List.of(), List.of(), List.of()));

        assertThat(client.streams(I1)).isEqualTo(new StreamData(List.of(0, 1), List.of(200, 210), List.of(140, 141)));
    }

    @Test
    void unknownActivityGivesReadableMessage() {
        when(intervals.streams(I1)).thenThrow(new IntervalsUnavailableException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                "unbekannt", null, null));

        assertThatThrownBy(() -> client.streams(I1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Aktivitaet i1 nicht gefunden");
    }

    @Test
    void unreachableServiceGivesReadableMessage() {
        when(intervals.streams(I1)).thenThrow(new IntervalsUnavailableException("intervals-service nicht erreichbar", null));

        assertThatThrownBy(() -> client.streams(I1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Streams nicht abrufbar: intervals-service nicht erreichbar");
    }
}
