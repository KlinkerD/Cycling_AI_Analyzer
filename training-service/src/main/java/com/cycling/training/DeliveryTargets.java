package com.cycling.training;

import com.cycling.messaging.contract.DeliveryMessage.Target;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Liest Versandziele aus der Konfiguration ({@code fitness.targets}, {@code recommendation.targets}),
 * z. B. "DISCORD:fitness,MAIL:default". Leer = Default-Ziele des delivery-service.
 */
public final class DeliveryTargets {

    private DeliveryTargets() {
    }

    /** @param key Name der Einstellung, nur fuer die Fehlermeldung */
    public static List<Target> parse(String key, String config) {
        if (config == null || config.isBlank()) return List.of();
        List<Target> targets = new ArrayList<>();
        for (String entry : config.split(",")) {
            String[] parts = entry.trim().split(":", -1);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw new IllegalStateException(key + ": '" + entry.trim()
                        + "' ist kein gueltiges Ziel, erwartet KANAL:ziel (z. B. DISCORD:fitness)");
            }
            targets.add(new Target(parts[0].trim().toUpperCase(Locale.ROOT), parts[1].trim()));
        }
        return List.copyOf(targets);
    }
}
