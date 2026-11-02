package com.cycling.messaging;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

/** JSON-(De)Serialisierung der MQTT-Payloads. Unbekannte Felder werden ignoriert (Vorwaertskompatibilitaet). */
public final class MessageCodec {

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public byte[] encode(Object message) {
        try {
            return mapper.writeValueAsBytes(message);
        } catch (IOException e) {
            throw new IllegalArgumentException("Nachricht nicht serialisierbar: " + message.getClass().getSimpleName(), e);
        }
    }

    public <T> T decode(byte[] payload, Class<T> type) {
        try {
            return mapper.readValue(payload, type);
        } catch (IOException e) {
            throw new IllegalArgumentException("Payload ist kein gueltiges " + type.getSimpleName() + ": " + e.getMessage(), e);
        }
    }
}
