package com.cycling.training.recommendation;

import com.cycling.intervals.api.IntervalsActivity;
import com.cycling.intervals.api.IntervalsWellnessPoint;
import com.cycling.intervals.client.IntervalsClient;

import java.time.LocalDate;
import java.util.List;

/** Holt Wellness und Aktivitaeten ueber den gemeinsamen {@link IntervalsClient} beim intervals-service. */
public class IntervalsRestClient implements IntervalsData {

    private final IntervalsClient client;

    public IntervalsRestClient(IntervalsClient client) {
        this.client = client;
    }

    @Override
    public List<IntervalsWellnessPoint> wellness(LocalDate from, LocalDate to) {
        return client.wellness(from, to);
    }

    @Override
    public List<IntervalsActivity> activities(LocalDate from, LocalDate to) {
        return client.activities(from, to);
    }
}
