package com.cycling.delivery;

import com.cycling.service.ReportReadyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Stoesst den Versand an, sobald eine Analyse fertig gespeichert ist. Fail-soft. */
@Component
public class ReportReadyListener {

    private static final Logger log = LoggerFactory.getLogger(ReportReadyListener.class);

    private final ReportDispatcher dispatcher;

    public ReportReadyListener(ReportDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @EventListener
    public void onReportReady(ReportReadyEvent event) {
        try {
            dispatcher.dispatch(event.report());
        } catch (RuntimeException e) {
            // Die Analyse ist gespeichert; der Versand kann per Button nachgeholt werden.
            log.warn("Report-Versand fuer {} konnte nicht angestossen werden: {}",
                    event.report().analysis().getActivityId(), e.getMessage());
        }
    }
}
