package com.cycling.report.api;

import com.cycling.types.ActivityId;

import com.cycling.report.ftp.FtpDetection;
import com.cycling.web.ApiNonNull;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** Ergebnis der FTP-Test-Erkennung einer Fahrt; die Werte sind nur bei DETECTED gesetzt. */
public record FtpDetectionDto(
        @ApiNonNull ActivityId activityId,
        @ApiNonNull @Schema(allowableValues = {"PENDING", "DETECTED", "NOT_DETECTED", "FAILED"}) String status,
        @Schema(description = "Watt, 20-Minuten-Mittel * 0,95") Integer ftp,
        @Schema(description = "bpm, mittlerer Puls im 20-Minuten-Block") Integer lthr,
        @Schema(description = "bpm, hoechstes 5-Sekunden-Mittel der Fahrt") Integer maxHr,
        @Schema(description = "Watt, mittlere Leistung im 20-Minuten-Block") Integer avgPower20Min,
        @Schema(description = "Start des 20-Minuten-Blocks in Sekunden ab Fahrtbeginn") Integer windowStartSeconds,
        String error,
        @ApiNonNull LocalDateTime requestedAt,
        LocalDateTime finishedAt
) {
    public static FtpDetectionDto from(FtpDetection d) {
        return new FtpDetectionDto(d.getActivityId(), d.getStatus().name(), d.getFtp(), d.getLthr(), d.getMaxHr(),
                d.getAvgPower20Min(), d.getWindowStartSeconds(), d.getError(), d.getRequestedAt(), d.getFinishedAt());
    }
}
