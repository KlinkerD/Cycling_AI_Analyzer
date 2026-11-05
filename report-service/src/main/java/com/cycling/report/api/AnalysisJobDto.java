package com.cycling.report.api;

import com.cycling.types.ActivityId;
import java.util.UUID;

import com.cycling.web.ApiNonNull;
import com.cycling.report.analysis.AnalysisJob;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** Status eines Analyseauftrags fuer das Frontend-Polling. */
public record AnalysisJobDto(
        @ApiNonNull UUID jobId,
        @ApiNonNull ActivityId activityId,
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
