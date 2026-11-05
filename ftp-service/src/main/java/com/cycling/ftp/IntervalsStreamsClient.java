package com.cycling.ftp;

import com.cycling.intervals.api.IntervalsStreams;
import com.cycling.intervals.client.IntervalsClient;
import com.cycling.intervals.client.IntervalsUnavailableException;
import com.cycling.types.ActivityId;

/** Holt die Streams einer Fahrt beim intervals-service, der die Daten besitzt und cacht. */
public class IntervalsStreamsClient {

    private final IntervalsClient client;

    public IntervalsStreamsClient(IntervalsClient client) {
        this.client = client;
    }

    /** @throws IllegalStateException mit lesbarer Meldung, wenn die Streams nicht abrufbar sind */
    public StreamData streams(ActivityId activityId) {
        IntervalsStreams streams;
        try {
            streams = client.streams(activityId);
        } catch (IntervalsUnavailableException e) {
            if (e.status().value() == 404) {
                throw new IllegalStateException("Aktivitaet " + activityId + " nicht gefunden", e);
            }
            throw new IllegalStateException("Streams nicht abrufbar: " + e.getMessage(), e);
        }
        return new StreamData(streams.time(), streams.watts(), streams.heartrate());
    }
}
