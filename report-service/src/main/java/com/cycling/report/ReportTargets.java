package com.cycling.report;

import com.cycling.messaging.contract.DeliveryMessage.Target;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Liest {@code report.targets}, z. B. "DISCORD:reports,MAIL:default". Leer = Default-Ziele des delivery-service. */
public final class ReportTargets {

    private ReportTargets() {
    }

    public static List<Target> parse(String config) {
        if (config == null || config.isBlank()) return List.of();
        List<Target> targets = new ArrayList<>();
        for (String entry : config.split(",")) {
            String[] parts = entry.trim().split(":", -1);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw new IllegalStateException(
                        "report.targets: '" + entry.trim() + "' ist kein gueltiges Ziel, erwartet KANAL:ziel (z. B. DISCORD:reports)");
            }
            targets.add(new Target(parts[0].trim().toUpperCase(Locale.ROOT), parts[1].trim()));
        }
        return List.copyOf(targets);
    }
}
