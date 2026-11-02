package com.cycling.dto;

import com.cycling.model.AnalysisJob;

import java.time.LocalDateTime;

/** Status eines Analyseauftrags fuer das Frontend-Polling. */
public record AnalysisJobDto(
        String jobId,
        String activityId,
        String status,
        String error,
        LocalDateTime createdAt,
        LocalDateTime finishedAt
) {
    public static AnalysisJobDto from(AnalysisJob job) {
        return new AnalysisJobDto(job.getId(), job.getActivityId(), job.getStatus().name(),
                job.getError(), job.getCreatedAt(), job.getFinishedAt());
    }
}
