package com.cycling.dto;

import com.cycling.config.ApiNonNull;
import com.cycling.model.AnalysisJob;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** Status eines Analyseauftrags fuer das Frontend-Polling. */
public record AnalysisJobDto(
        @ApiNonNull String jobId,
        @ApiNonNull String activityId,
        @ApiNonNull @Schema(allowableValues = {"PENDING", "DONE", "FAILED"}) String status,
        String error,
        @ApiNonNull LocalDateTime createdAt,
        LocalDateTime finishedAt
) {
    public static AnalysisJobDto from(AnalysisJob job) {
        return new AnalysisJobDto(job.getId(), job.getActivityId(), job.getStatus().name(),
                job.getError(), job.getCreatedAt(), job.getFinishedAt());
    }
}
