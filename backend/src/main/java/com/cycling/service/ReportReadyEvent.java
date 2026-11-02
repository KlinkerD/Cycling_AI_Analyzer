package com.cycling.service;

import com.cycling.dto.ReportDto;

/** Eine Analyse ist fertig gespeichert; Listener stossen z. B. den Versand an. */
public record ReportReadyEvent(ReportDto report) {
}
