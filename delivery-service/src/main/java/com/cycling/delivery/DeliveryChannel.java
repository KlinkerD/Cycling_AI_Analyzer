package com.cycling.delivery;

import java.util.Locale;
import java.util.Optional;

/** Versandkanaele. */
public enum DeliveryChannel {
    MAIL,
    DISCORD;

    /** Kanal aus einem Ziel der Nachricht ("discord" / "DISCORD"); leer bei unbekanntem Namen. */
    public static Optional<DeliveryChannel> parse(String name) {
        if (name == null) return Optional.empty();
        try {
            return Optional.of(valueOf(name.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
