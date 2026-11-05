package com.cycling.report;

import com.cycling.types.ActivityId;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Speichert {@link ActivityId} als einfachen Text, damit die bestehenden Spalten ({@code activity_id}) bleiben.
 * Gilt automatisch fuer alle Entity-Felder dieses Typs (nicht fuer {@code @Id}, siehe {@code FtpDetection}).
 */
@Converter(autoApply = true)
public class ActivityIdConverter implements AttributeConverter<ActivityId, String> {

    @Override
    public String convertToDatabaseColumn(ActivityId id) {
        return id == null ? null : id.value();
    }

    @Override
    public ActivityId convertToEntityAttribute(String value) {
        return value == null || value.isBlank() ? null : ActivityId.of(value);
    }
}
