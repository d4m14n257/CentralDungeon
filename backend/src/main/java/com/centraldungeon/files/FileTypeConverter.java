package com.centraldungeon.files;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Maps {@link FileType} to the value {@code files.file_type} actually holds.
 *
 * <p>{@code @Enumerated(EnumType.STRING)} would write the constant's name, and one of the three
 * values is {@code Single-use} - a hyphen no Java identifier can carry. The baseline inherited that
 * spelling from the legacy's own column (M21.1) and rows in the wild already use it, so the mapping
 * is declared here rather than bending either side to match the other.
 *
 * <p>Only the database sees the hyphen. What crosses HTTP is the constant's name
 * ({@code SingleUse}), which is what arquitectura.md 2.3 asks for and what the frontend models as a
 * union of literals.
 */
@Converter(autoApply = true)
public class FileTypeConverter implements AttributeConverter<FileType, String> {

    /**
     * @param attribute the type as the entity holds it, or null on an unset field
     * @return the value to write to the column, or null
     */
    @Override
    public String convertToDatabaseColumn(FileType attribute) {
        return attribute == null ? null : attribute.storedValue();
    }

    /**
     * @param dbData the value read from the column, or null
     * @return the matching constant, or null
     * @throws IllegalArgumentException if the column holds a value no constant covers - a row written
     *                                  outside the application, which is a bug and answers 500
     */
    @Override
    public FileType convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return FileType.fromStoredValue(dbData)
                .orElseThrow(() -> new IllegalArgumentException("Unknown file type in the database: " + dbData));
    }
}
