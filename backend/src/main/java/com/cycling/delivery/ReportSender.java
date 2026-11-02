package com.cycling.delivery;

import com.cycling.dto.ReportDto;

/** Ein Versandkanal (Mail, Discord, ...). */
public interface ReportSender {

    DeliveryChannel channel();

    boolean isEnabled();

    /**
     * Sendet den Report. Kanaele mit mehreren Teilen (Discord: Text + Charts) lesen
     * {@link ReportDelivery#getCompletedParts()} und melden fertige Teile ueber
     * {@link ReportDelivery#markPartCompleted()}, damit ein Retry nur den Rest sendet.
     */
    void send(ReportDto report, ReportCharts charts, ReportDelivery delivery) throws DeliveryException;
}
