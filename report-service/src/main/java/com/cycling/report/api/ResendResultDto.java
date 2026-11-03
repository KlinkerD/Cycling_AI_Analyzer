package com.cycling.report.api;

import com.cycling.web.ApiNonNull;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Antwort auf "Report erneut senden". */
public record ResendResultDto(
        @ApiNonNull @Schema(allowableValues = {"queued", "disabled", "unavailable"}) String status,
        /** Kanaele, an die gesendet wird (z. B. MAIL, DISCORD); leer, wenn nicht "queued". */
        List<String> channels,
        String message
) {
    public static ResendResultDto queued(List<String> channels) {
        return new ResendResultDto("queued", channels, null);
    }

    public static ResendResultDto failed(String status, String message) {
        return new ResendResultDto(status, List.of(), message);
    }
}
