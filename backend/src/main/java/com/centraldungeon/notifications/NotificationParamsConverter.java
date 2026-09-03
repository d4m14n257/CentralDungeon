package com.centraldungeon.notifications;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stores {@link NotificationParams} as JSON in {@code notifications.params}, and reads it back.
 *
 * <p>Its own {@link JsonMapper} rather than the application's: this one serializes a single closed
 * record and must keep doing exactly that for rows written years ago. Sharing the web mapper would
 * mean a configuration change made for an HTTP response could silently change how history is read.
 *
 * <p>Jackson 3 ({@code tools.jackson.*}), not the Jackson 2 packages most Boot 3 material shows.
 */
@Converter
public class NotificationParamsConverter implements AttributeConverter<NotificationParams, String> {

    /** Immutable and thread-safe once built, which is what lets one instance serve every row. */
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /**
     * Serializes the parameters for storage.
     *
     * @param attribute the parameters, or null for a row that needs none
     * @return the JSON to store, or null when there is nothing to store
     */
    @Override
    public @Nullable String convertToDatabaseColumn(@Nullable NotificationParams attribute) {
        return attribute == null ? null : MAPPER.writeValueAsString(attribute);
    }

    /**
     * Reads the parameters back.
     *
     * @param dbData the stored JSON, or null for a row written before #197
     * @return the parameters, or null when the row has none - the caller falls back to the text that
     *         was frozen into {@code title} and {@code message} at the time
     */
    @Override
    public @Nullable NotificationParams convertToEntityAttribute(@Nullable String dbData) {
        return dbData == null || dbData.isBlank() ? null : MAPPER.readValue(dbData, NotificationParams.class);
    }
}
