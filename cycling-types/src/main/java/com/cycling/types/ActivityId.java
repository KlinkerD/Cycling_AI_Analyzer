package com.cycling.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.io.Serializable;

/**
 * ID einer Aktivitaet bei intervals.icu, z. B. "i123456". Eigener Typ, damit sie nicht mit anderen Strings
 * verwechselt wird; im JSON (MQTT, REST) und in der Datenbank bleibt sie ein einfacher String.
 *
 * @param value nicht leer
 */
public record ActivityId(@JsonValue String value) implements Serializable, Comparable<ActivityId> {

    public ActivityId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ActivityId darf nicht leer sein");
        }
    }

    /** Fuer JSON und Spring (Pfadvariablen): {@code "i123"} wird {@code ActivityId[i123]}. */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ActivityId of(String value) {
        return new ActivityId(value);
    }

    @Override
    public int compareTo(ActivityId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
