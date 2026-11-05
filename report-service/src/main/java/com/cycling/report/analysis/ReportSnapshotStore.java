package com.cycling.report.analysis;

import com.cycling.types.ActivityId;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Optional;

/** Liest und schreibt die {@link ReportData} einer Fahrt (JSON in {@link ReportSnapshot}). */
@Component
public class ReportSnapshotStore {

    private static final Logger log = LoggerFactory.getLogger(ReportSnapshotStore.class);
    private static final ObjectMapper MAPPER = AnalysisJobService.jobJsonMapper();

    private final ReportSnapshotRepository repository;
    private final Clock clock;

    public ReportSnapshotStore(ReportSnapshotRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Leer, wenn es keinen Snapshot gibt oder er nicht lesbar ist (dann wird er neu geladen). */
    public Optional<ReportData> load(ActivityId activityId) {
        return repository.findByActivityId(activityId).flatMap(s -> {
            try {
                return Optional.of(MAPPER.readValue(s.getDataJson(), ReportData.class));
            } catch (JsonProcessingException e) {
                log.warn("Gespeicherte Report-Daten fuer {} nicht lesbar, werden neu geladen: {}", activityId,
                        e.getMessage());
                return Optional.empty();
            }
        });
    }

    /** Legt den Snapshot an oder ersetzt ihn. */
    public void save(ActivityId activityId, ReportData data) {
        String json;
        try {
            json = MAPPER.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Report-Daten nicht serialisierbar: " + e.getMessage(), e);
        }
        ReportSnapshot snapshot = repository.findByActivityId(activityId)
                .map(s -> {
                    s.update(json, clock.instant());
                    return s;
                })
                .orElseGet(() -> new ReportSnapshot(activityId, json, clock.instant()));
        repository.save(snapshot);
    }
}
